#!/usr/bin/env bash

curl -X 'PUT' \
  'http://localhost:3009/' \
  -H 'accept: application/json' \
  -H 'x-rems-api-key: 511a9e58-866a-4488-b052-12058c1dc51b' \
  -H 'x-rems-user-id: http://cilogon.org/serverE/users/26179' \
  -H 'Content-Type: application/json' \
  -d '{
    "event/id": "TEST_EVENT",
    "event/type": "blacklist.event/add",
    "event/blacklist": {
        "blacklist/user": {
            "userid": "http://cilogon.org/serverE/users/26416",
            "name": "Alex Ip",
            "email": "ipai3458@gmail.com"
        },
        "blacklist/resource": {
            "resource/ext-id": "https://dev-binderhub.atap-binder.cloud.edu.au/"
        },
        "blacklist/comment": "Test auto-revoker API calls",
        "blacklist/added-by": {
            "userid": "http://cilogon.org/serverE/users/26179",
            "name": "Alex Ip",
            "email": "Alex.Ip@aarnet.edu.au"
        },
        "blacklist/added-at": "2023-02-28T00:30:51.037Z"
    }
}'