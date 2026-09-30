[Unit]
Description=Volo UI build verification
After=network.target

[Service]
Type=oneshot
ExecStart=
Restart=no

[Install]
WantedBy=multi-user.target
