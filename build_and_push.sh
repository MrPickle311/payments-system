sudo docker build -t registry.homelab/payment-service:latest ./payment-service
sudo docker build -t registry.homelab/wallet-service:latest ./wallet-service
sudo docker build -t registry.homelab/ledger-service:latest ./ledger-service

sudo docker push registry.homelab/payment-service:latest
sudo docker push registry.homelab/wallet-service:latest
sudo docker push registry.homelab/ledger-service:latest

