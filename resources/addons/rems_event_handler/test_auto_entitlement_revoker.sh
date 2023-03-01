#!/usr/bin/env bash

curl -X 'PUT' \
  'http://localhost:3009/event' \
  -H 'accept: */*' \
  -H 'Content-Type: application/json' \
  -d '{
    "event/id": "TEST_EVENT",
    "event/type": "application.event/revoked",
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