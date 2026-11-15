#!/usr/bin/env python3
"""HTTP checks against the local API. Requires a running Docker Compose stack."""
import json
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError

settings = dict(line.split('=', 1) for line in Path('.env').read_text().splitlines() if '=' in line)

def call(method, path, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = Request('http://127.0.0.1:8080' + path,
                  data=json.dumps(body).encode() if body is not None else None,
                  headers=headers, method=method)
    try:
        with urlopen(req, timeout=10) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, error.read().decode()

assert call('GET', '/api/data')[0] == 401
assert call('POST', '/auth/login', {'username': "' OR 1=1 --", 'password': 'wrong'})[0] == 401
status, login = call('POST', '/auth/login', {'username': settings['DEMO_USERNAME'], 'password': settings['DEMO_PASSWORD']})
assert status == 200
token = login['accessToken']
assert call('GET', '/api/data', token='invalid')[0] == 401
assert call('POST', '/api/data', {'title': '', 'content': 'test'}, token)[0] == 400
status, note = call('POST', '/api/data', {'title': '<script>alert(1)</script>', 'content': "Robert'); DROP TABLE notes;--"}, token)
assert status == 201
assert note['title'] == '&lt;script&gt;alert(1)&lt;/script&gt;'
status, notes = call('GET', '/api/data', token=token)
assert status == 200 and any(item['id'] == note['id'] for item in notes)
print('PASS: authentication, SQLi, XSS, validation, create and list')
