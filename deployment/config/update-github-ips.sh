#!/bin/bash
# Update GitHub Actions IP whitelist
# /etc/cron.d/update-github-ips:0 2 * * * root /usr/local/bin/update-github-ips.sh
# Create sets if they don't exist
ipset create github-v4 hash:net family inet maxelem 65536 -exist
ipset create github-v6 hash:net family inet6 maxelem 65536 -exist

# Flush existing entries
ipset flush github-v4
ipset flush github-v6

# Fetch and add GitHub IPs using batch method
curl -s https://api.github.com/meta | jq -r '.actions[]' | {
    while IFS= read -r ip; do
        if [[ "$ip" == *":"* ]]; then
            echo "add github-v6 $ip"
        else
            echo "add github-v4 $ip"
        fi
    done
} | ipset restore -exist

# Log the update
echo "$(date): Updated GitHub IPs - IPv4: $(ipset list github-v4 | tail -n +9 | wc -l), IPv6: $(ipset list github-v6 | tail -n +9 | wc -l)" >> /var/log/github-ips-update.log