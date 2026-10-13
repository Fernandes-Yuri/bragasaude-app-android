"""Transfere apenas código/manifesto públicos ao host autorizado via SSM."""
import argparse
import base64
import json
from pathlib import Path
import subprocess
import tempfile
import time

INSTANCE = 'i-04728dd8094ed0e78'


def command(client, manifest=None):
    code = base64.b64encode(Path(client).read_bytes()).decode()
    data = base64.b64encode(Path(manifest).read_bytes()).decode() if manifest else ''
    mode = '--manifest "$task_dir/manifest.json"' if manifest else '--check'
    return f'''set -eu
umask 077
task_dir=$(mktemp -d /tmp/braga-release.XXXXXX)
trap 'rm -rf "$task_dir"' EXIT
printf '%s' '{code}' | base64 -d > "$task_dir/client.py"
printf '%s' '{data}' | base64 -d > "$task_dir/manifest.json"
chown -R ec2-user:ec2-user "$task_dir"
runuser -u ec2-user -- python3 "$task_dir/client.py" {mode}
'''


def aws(*args):
    return subprocess.run(['aws', '--region', 'us-east-2', *args], check=True, capture_output=True, text=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--client', default='.github/scripts/publish_apk.py')
    parser.add_argument('--manifest')
    args = parser.parse_args()
    request = {'InstanceIds': [INSTANCE], 'DocumentName': 'AWS-RunShellScript',
               'TimeoutSeconds': 600, 'Comment': 'Publish verified APK through private gateway control',
               'Parameters': {'commands': [command(args.client, args.manifest)]}}
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / 'request.json'; path.write_text(json.dumps(request))
        sent = json.loads(aws('ssm', 'send-command', '--cli-input-json', f'file://{path}').stdout)
    command_id = sent['Command']['CommandId']
    print(f'SSM command: {command_id}', flush=True)
    for _ in range(200):
        time.sleep(3)
        try:
            status = json.loads(aws('ssm', 'get-command-invocation', '--command-id', command_id,
                                   '--instance-id', INSTANCE).stdout)
        except subprocess.CalledProcessError as error:
            if 'InvocationDoesNotExist' in error.stderr:
                continue
            raise
        if status['Status'] in {'Pending', 'InProgress', 'Delayed'}:
            continue
        if status['Status'] != 'Success':
            raise RuntimeError(f"SSM publication failed: {status['Status']}")
        result = json.loads(status['StandardOutputContent'].strip())
        if result.get('status') not in {'ready', 'published', 'already_active'}:
            raise RuntimeError('Gateway did not confirm publication')
        print(json.dumps(result))
        return
    raise TimeoutError('Publication status unknown; retry is idempotent')


if __name__ == '__main__':
    main()
