docker build -t registry.homelab/payment-service:latest ./payment-service
docker build -t registry.homelab/wallet-service:latest ./wallet-service
docker build -t registry.homelab/ledger-service:latest ./ledger-service
docker build -t registry.homelab/export-batch-service:latest ./export-batch-service
docker build -t registry.homelab/mock-regulatory-service:latest ./mock-regulatory-service
docker build -t registry.homelab/authorization-service:latest ./authorization-service
docker build -t registry.homelab/fx-service:latest ./fx-service
docker build -t registry.homelab/fee-service:latest ./fee-service
docker build -t registry.homelab/fraud-service:latest ./fraud-service
docker build -t registry.homelab/limits-service:latest ./limits-service
docker build -t registry.homelab/sanctions-service:latest ./sanctions-service
docker build -t registry.homelab/webhooks-service:latest ./webhooks-service
docker build -t registry.homelab/outbox-relay-service:latest ./outbox-relay-service
#
docker push registry.homelab/payment-service:latest
docker push registry.homelab/wallet-service:latest
docker push registry.homelab/ledger-service:latest
docker push registry.homelab/export-batch-service:latest
docker push registry.homelab/mock-regulatory-service:latest
docker push registry.homelab/authorization-service:latest
docker push registry.homelab/fx-service:latest
docker push registry.homelab/fee-service:latest
docker push registry.homelab/fraud-service:latest
docker push registry.homelab/limits-service:latest
docker push registry.homelab/sanctions-service:latest
docker push registry.homelab/webhooks-service:latest
docker push registry.homelab/outbox-relay-service:latest

kubectl rollout restart deployment payment-service  -n applications
kubectl rollout restart deployment webhooks-service  -n applications
kubectl rollout restart deployment fx-service  -n applications
kubectl rollout restart deployment limits-service  -n applications
kubectl rollout restart deployment authorization-service  -n applications
kubectl rollout restart deployment fraud-service  -n applications
kubectl rollout restart deployment sanctions-service  -n applications
kubectl rollout restart deployment wallet-service  -n applications
kubectl rollout restart deployment ledger-service  -n applications
kubectl rollout restart deployment export-batch-manager  -n applications
kubectl rollout restart deployment export-batch-listener  -n applications
kubectl rollout restart deployment export-batch-retry-manager  -n applications
