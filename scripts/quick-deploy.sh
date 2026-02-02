#!/bin/bash
set -e

# Quick local build and deploy
# Validates git state, runs tests, pushes, builds, and deploys

cd "$(dirname "$0")/.."

# Use SDKman Java if available
if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
    export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}🔍 Checking git state...${NC}"

# Fetch latest from origin
git fetch origin main --quiet

# Check if we're on a branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "   Current branch: $CURRENT_BRANCH"

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}❌ Uncommitted changes detected. Commit or stash first.${NC}"
    git status --short
    exit 1
fi

# Check if current branch contains origin/main
if ! git merge-base --is-ancestor origin/main HEAD; then
    echo -e "${RED}❌ Branch is not up to date with origin/main${NC}"
    echo "   Run: git merge origin/main  OR  git rebase origin/main"
    exit 1
fi

# Check if we're ahead of origin (something to push)
LOCAL=$(git rev-parse HEAD)
if [ "$CURRENT_BRANCH" = "main" ]; then
    REMOTE=$(git rev-parse origin/main)
    if [ "$LOCAL" = "$REMOTE" ]; then
        echo -e "${YELLOW}⚠️  No new commits to push. Deploying existing...${NC}"
    fi
fi

echo -e "${GREEN}✓ Git state OK${NC}"

echo -e "\n${YELLOW}🧪 Running tests...${NC}"
if ! ./gradlew check --quiet; then
    echo -e "${RED}❌ Tests failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Tests passed${NC}"

echo -e "\n${YELLOW}📤 Pushing to origin...${NC}"
git push origin "$CURRENT_BRANCH"
echo -e "${GREEN}✓ Pushed${NC}"

echo -e "\n${YELLOW}🔨 Building JAR...${NC}"
./gradlew bootJar -x test --quiet

echo -e "\n${YELLOW}🐳 Building Docker image...${NC}"
docker build -t ghcr.io/knobo/simple-queue:latest . --quiet

echo -e "\n${YELLOW}📤 Pushing to container registry...${NC}"
docker push ghcr.io/knobo/simple-queue:latest --quiet

echo -e "\n${YELLOW}🔄 Restarting deployment...${NC}"
export KUBECONFIG=~/.kube/config
kubectl rollout restart deployment/simple-queue -n queue

echo -e "\n${YELLOW}⏳ Waiting for rollout...${NC}"
kubectl rollout status deployment/simple-queue -n queue --timeout=120s

echo -e "\n${GREEN}✅ Deploy complete!${NC}"
echo "   View logs: kubectl logs -f deployment/simple-queue -n queue"
