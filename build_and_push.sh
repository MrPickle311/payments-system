sudo docker build -t registry.homelab/payment-service:latest ./payment-service
#sudo docker build -t registry.homelab/wallet-service:latest ./wallet-service
#sudo docker build -t registry.homelab/ledger-service:latest ./ledger-service
sudo docker build -t registry.homelab/export-batch-service:latest ./export-batch-service
#sudo docker build -t registry.homelab/mock-regulatory-service:latest ./mock-regulatory-service

sudo docker push registry.homelab/payment-service:latest
#sudo docker push registry.homelab/wallet-service:latest
#sudo docker push registry.homelab/ledger-service:latest
sudo docker push registry.homelab/export-batch-service:latest
#sudo docker push registry.homelab/mock-regulatory-service:latest
