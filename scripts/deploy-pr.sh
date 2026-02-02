#!/bin/bash
set -e

# Usage: ./deploy-pr.sh <PR_NUMBER> [--delete]

PR_NUMBER=$1
ACTION=$2

if [ -z "$PR_NUMBER" ]; then
    echo "Usage: $0 <PR_NUMBER> [--delete]"
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# Use SDKman Java if available
if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

NAMESPACE="simple-queue-staging"
URL="https://pr-${PR_NUMBER}.queue.knobo.no"
TEMPLATE="/home/knobo/prog/n8n/k8s-queue-staging/pr-template.yaml"

# Cleanup mode
if [ "$ACTION" == "--delete" ]; then
    echo "🗑️  Deleting PR-${PR_NUMBER} environment..."
    export KUBECONFIG=~/.kube/config
    
    # Drop database
    POSTGRES_POD=$(kubectl get pods -n "$NAMESPACE" -l app=postgres -o jsonpath="{.items[0].metadata.name}")
    if [ -n "$POSTGRES_POD" ]; then
         echo "   Dropping database queue_pr_${PR_NUMBER}..."
         kubectl exec -n "$NAMESPACE" "$POSTGRES_POD" -- dropdb -U postgres "queue_pr_${PR_NUMBER}" || true
    fi

    kubectl delete deployment "simple-queue-pr-${PR_NUMBER}" -n "$NAMESPACE" --ignore-not-found
    kubectl delete service "simple-queue-pr-${PR_NUMBER}" -n "$NAMESPACE" --ignore-not-found
    kubectl delete ingress "simple-queue-pr-${PR_NUMBER}" -n "$NAMESPACE" --ignore-not-found
    echo "✅ Cleanup complete."
    exit 0
fi

echo "🚀 Deploying PR-${PR_NUMBER} to staging..."

# 1. Build JAR
echo "🔨 Building JAR..."
./gradlew bootJar -x test --quiet

# 2. Build Docker Image
IMAGE_TAG="ghcr.io/knobo/simple-queue:pr-${PR_NUMBER}"
echo "🐳 Building Docker image: $IMAGE_TAG"
docker build -t "$IMAGE_TAG" . --quiet

# 3. Push Image
echo "sc Push Docker image..."
docker push "$IMAGE_TAG" --quiet

# 4. Prepare Database
echo "🗄️  Preparing database queue_pr_${PR_NUMBER}..."
POSTGRES_POD=$(kubectl get pods -n "$NAMESPACE" -l app=postgres -o jsonpath="{.items[0].metadata.name}")
if [ -n "$POSTGRES_POD" ]; then
    kubectl exec -n "$NAMESPACE" "$POSTGRES_POD" -- createdb -U postgres "queue_pr_${PR_NUMBER}" || true
else
    echo "⚠️  Postgres pod not found! Skipping database creation."
fi

# 5. Deploy to K8s
echo "☸️  Deploying to Kubernetes..."
export PR_NUMBER
export KUBECONFIG=~/.kube/config

# Generate manifest
envsubst < "$TEMPLATE" | kubectl apply -f -

# 6. Wait for rollout
echo "⏳ Waiting for rollout..."
kubectl rollout status "deployment/simple-queue-pr-${PR_NUMBER}" -n "$NAMESPACE" --timeout=60s

# 7. Update GitHub Status (if gh is logged in)
if command -v gh &> /dev/null && gh auth status &> /dev/null; then
    echo "octocat Updating GitHub status..."
    gh api --method POST \
      -H "Accept: application/vnd.github+json" \
      "/repos/knobo/simple-queue/statuses/$(git rev-parse HEAD)" \
      -f state='success' \
      -f target_url="$URL" \
      -f description='Deployed to staging' \
      -f context='continuous-deployment/staging' || echo "⚠️ Failed to update GitHub status"
fi

echo "✅ PR-${PR_NUMBER} deployed successfully!"
echo "🌍 URL: $URL"
