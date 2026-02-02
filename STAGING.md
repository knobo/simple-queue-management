# Staging Environment Setup

## Overview
A new staging environment `simple-queue-staging` has been configured to support isolated PR testing.

## usage
### Deploy a PR
```bash
./scripts/deploy-pr.sh <PR_NUMBER>
```
This will:
1. Build the code and Docker image (`pr-<N>`).
2. Create a dedicated database `queue_pr_<N>` in staging.
3. Deploy the app to `pr-<N>.queue.knobo.no`.
4. Update GitHub commit status.

### Teardown a PR
```bash
./scripts/deploy-pr.sh <PR_NUMBER> --delete
```
This removes the deployment and drops the database.

## Infrastructure
- **Namespace:** `simple-queue-staging`
- **Database:** Shared Postgres instance `postgres.simple-queue-staging`.
- **Secrets:** Copied from `queue` namespace.
- **Manifests:** Located in `/home/knobo/prog/n8n/k8s-queue-staging/`.
