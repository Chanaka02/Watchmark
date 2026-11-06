# Hosting on Microsoft Azure (VM + Docker Compose + Caddy)

Step-by-step for running Watchmark on one Azure Linux VM with the files in
`deploy/`, which were tested locally (see [deployment.md](deployment.md)). The
general guide still applies; this page adds the Azure-specific parts.

**This costs money** from the moment the VM exists, even when stopped (disk and
public IP). Check prices for your region in the Azure Pricing Calculator first,
and set a budget alert (step 2). Azure for Students credit can cover it for a
while. The free-account `B1s` VM (1 GB) is too small for Java plus MySQL.

What you end up with:

```
https://watchmark-<name>.<region>.cloudapp.azure.com   (free Azure DNS name, real HTTPS certificate)
  Azure VM, Ubuntu 24.04, Standard_B2s (2 vCPU, 4 GB)
    Docker: web (Caddy) → backend → mysql
  Nightly database dumps → Azure Blob Storage
```

Commands in steps 1–2 and 9 run on **your computer** (Azure CLI). Steps 3–8 run
**on the VM** over SSH.

## 0. Before you start

- An Azure subscription (Azure for Students, Pay-As-You-Go, or similar).
- The Azure CLI on your computer (`winget install Microsoft.AzureCLI`), then
  sign in:

```powershell
az login
az account show --query "{name:name, id:id}"     # the subscription that will be billed
```

- Pick a **region** close to you, e.g. `southeastasia` (Singapore) or
  `centralindia` (Pune) for Sri Lanka, and a **name** for the DNS label
  (lowercase letters, numbers, hyphens), e.g. `watchmark-chanaka`.
- Your TMDB API Read Access Token (themoviedb.org → Settings → API).

## 1. Create the VM

```powershell
$rg = "watchmark-rg"; $loc = "southeastasia"; $dns = "watchmark-chanaka"

az group create --name $rg --location $loc

az vm create --resource-group $rg --name watchmark-vm `
  --image Ubuntu2404 --size Standard_B2s --os-disk-size-gb 64 `
  --admin-username deploy --generate-ssh-keys `
  --public-ip-sku Standard --public-ip-address-dns-name $dns
```

- `--generate-ssh-keys` uses (or creates) your SSH key in `~\.ssh`. The VM accepts
  only that key, with no password login.
- If `Standard_B2s` is not available in the region, list alternatives with
  `az vm list-skus --location $loc --size Standard_B --output table`
  (`Standard_B2als_v2` is similar), or try another region.

Open the web ports, and allow SSH only from your own IP address:

```powershell
az vm open-port --resource-group $rg --name watchmark-vm --port 80,443 --priority 1010

$myip = (Invoke-RestMethod https://api.ipify.org)
az network nsg rule update --resource-group $rg --nsg-name watchmark-vmNSG `
  --name default-allow-ssh --source-address-prefixes "$myip/32"
```

If your home IP changes, run the last two lines again (or SSH is refused).

Get the address:

```powershell
az vm show -d --resource-group $rg --name watchmark-vm --query fqdns --output tsv
# e.g. watchmark-chanaka.southeastasia.cloudapp.azure.com
```

## 2. Budget alert (recommended)

Azure Portal → **Cost Management** → **Budgets** → *Add*: scope your
subscription, a monthly amount you are comfortable with, and an e-mail alert at
80% and 100%. A budget alerts; it does not stop spending.

## 3. Prepare the VM

```powershell
ssh deploy@watchmark-chanaka.southeastasia.cloudapp.azure.com
```

On the VM:

```sh
sudo apt update && sudo apt -y upgrade
sudo apt -y install unattended-upgrades git
sudo dpkg-reconfigure -plow unattended-upgrades     # automatic security updates: Yes

# Docker Engine and the Compose plugin (Docker's official install script)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy
exit                                               # log out and back in for the docker group
```

The Azure network security group from step 1 is the firewall: only 80, 443 and
(from your IP) 22 are open. MySQL and the backend publish no ports at all.

Optionally, cap Docker's container logs (they are never rotated by default):

```sh
echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"5"}}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
```

## 4. Get the code (private repository, read-only key)

On the VM, create a key that can only **read** this one repository:

```sh
ssh-keygen -t ed25519 -f ~/.ssh/watchmark_deploy -N "" -C "watchmark azure vm"
cat ~/.ssh/watchmark_deploy.pub
```

On GitHub: repository **Settings → Deploy keys → Add deploy key**, paste the
public key, and leave **Allow write access unticked**. Then on the VM:

```sh
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/watchmark_deploy
  IdentitiesOnly yes
EOF
sudo mkdir -p /srv/watchmark && sudo chown deploy:deploy /srv/watchmark
git clone git@github.com:Chanaka02/Watchmark.git /srv/watchmark
cd /srv/watchmark
```

## 5. Configure

```sh
cp deploy/production.env.example deploy/production.env
chmod 600 deploy/production.env
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|; s|^DB_ROOT_PASSWORD=.*|DB_ROOT_PASSWORD=$(openssl rand -hex 24)|" deploy/production.env
nano deploy/production.env
```

In the editor, set:

```
SITE_ADDRESS=watchmark-chanaka.southeastasia.cloudapp.azure.com
TMDB_READ_ACCESS_TOKEN=<your token>
METADATA_PROVIDER=tmdb
```

The passwords were filled in by the `sed` line. Never commit this file (it is
git-ignored).

## 6. Build and start

```sh
cd /srv/watchmark
TAG=$(git rev-parse --short HEAD)
docker build -t watchmark-backend:$TAG backend                 # a few minutes the first time
docker build -f deploy/web.Dockerfile -t watchmark-web:$TAG .
sed -i "s/^BACKEND_IMAGE=.*/BACKEND_IMAGE=watchmark-backend:$TAG/; s/^WEB_IMAGE=.*/WEB_IMAGE=watchmark-web:$TAG/" deploy/production.env

docker compose -f deploy/compose.yml --env-file deploy/production.env up -d --no-build
docker compose -f deploy/compose.yml --env-file deploy/production.env ps
docker compose -f deploy/compose.yml --env-file deploy/production.env logs -f web backend   # Ctrl+C to leave
```

Caddy requests the HTTPS certificate on the first start (ports 80 and 443 must
be reachable, which step 1 arranged). The backend logs `Started
WatchmarkBackendApplication` when ready; with a missing or wrong TMDB token it
stops instead, by design.

## 7. Check it

Open `https://watchmark-chanaka.southeastasia.cloudapp.azure.com`, register,
add a show and mark an episode watched. From your computer:

```powershell
curl.exe -sI https://watchmark-chanaka.southeastasia.cloudapp.azure.com/ | findstr /i "strict-transport content-security"
curl.exe -s https://watchmark-chanaka.southeastasia.cloudapp.azure.com/api/v1/catalog/source
# {"provider":"tmdb","mock":false}
```

## 8. Backups

**Nightly dump on the VM** (crontab of `deploy`: `crontab -e`):

```
15 2 * * * cd /srv/watchmark && deploy/backup.sh >> backups/backup.log 2>&1
```

**Off-VM copy to Azure Blob Storage.** The VM signs in with its own managed
identity, so no storage key is stored anywhere. On your computer (the storage
account name must be globally unique, 3–24 lowercase letters and digits):

```powershell
$sa = "watchmarkbackups$((Get-Random -Maximum 99999))"
az storage account create --name $sa --resource-group $rg --location $loc `
  --sku Standard_LRS --min-tls-version TLS1_2 --allow-blob-public-access false
az storage container create --name watchmark-backups --account-name $sa --auth-mode key

$principal = az vm identity assign --resource-group $rg --name watchmark-vm --query systemAssignedIdentity --output tsv
$scope = az storage account show --name $sa --resource-group $rg --query id --output tsv
az role assignment create --assignee-object-id $principal --assignee-principal-type ServicePrincipal `
  --role "Storage Blob Data Contributor" --scope $scope
$sa    # note the name for the next step
```

On the VM, install AzCopy and add the copy to the crontab right after the
backup (replace `<storage-account>`):

```sh
curl -sL https://aka.ms/downloadazcopy-v10-linux | tar -xz --strip-components=1 -C /tmp
sudo mv /tmp/azcopy /usr/local/bin/azcopy && azcopy --version
```

```
45 2 * * * cd /srv/watchmark && AZCOPY_AUTO_LOGIN_TYPE=MSI azcopy copy 'backups/*.sql.gz' 'https://<storage-account>.blob.core.windows.net/watchmark-backups/' --overwrite=false >> backups/offsite.log 2>&1
```

Run both lines once by hand to check them. Rehearse a restore every few months
([deployment.md](deployment.md), step 5). Azure Backup for the whole VM is an
optional, paid extra.

## 9. Updating, rollback, and removing everything

- **Update / rollback / restore:** exactly as in [deployment.md](deployment.md)
  steps 5–7 (always back up before an update).
- **Stop paying for it:** stopping (deallocating) the VM stops compute charges,
  but the disk, public IP and storage still cost a little. To remove
  **everything**, including the data:

```powershell
az group delete --name watchmark-rg     # irreversible: copy the backups you want to keep first
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ssh` times out | Your IP changed: rerun the `nsg rule update` from step 1. |
| Browser shows a certificate error | Check `docker compose … logs web`: ports 80/443 must be open, and `SITE_ADDRESS` must equal the VM's DNS name exactly. |
| Backend keeps restarting | `docker compose … logs backend`: usually a missing TMDB token or a typo in `deploy/production.env`. |
| `docker: permission denied` | Log out and back in after `usermod -aG docker deploy`. |
| Slow or out of memory | `docker stats`; a 4 GB VM is comfortable, 2 GB is tight. |
