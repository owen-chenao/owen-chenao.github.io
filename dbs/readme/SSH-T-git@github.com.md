# SSH

```
ssh -T git@github.com
```

# Config配置调整

```
Host github.com
  HostName github.com
  Port 443
  User git
  PreferredAuthentications publickey
  IdentityFile ~/.ssh/id_rsa
  HostKeyAlgorithms +ssh-rsa
  PubkeyAcceptedAlgorithms +ssh-rsa
```