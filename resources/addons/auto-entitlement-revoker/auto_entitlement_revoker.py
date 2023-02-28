#!/usr/bin/env python3

# A simple HTTP server listening REMS 'blacklist.event/add' event notifications.
# On notification it tries to revoke any existing entitlements for that user & resource.

# A configuration file 'config.ini' must be supplied.

# Usage: ./auto_entitlement_revoker.py
#        Stop with Ctrl-C

import configparser
import http.server
import json
import logging

import requests

EVENT_TYPE = 'blacklist.event/add'

logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s', level=logging.DEBUG)
log = logging.getLogger(__name__)

parser = configparser.ConfigParser()
try:
    parser.read('config.ini')
    config = parser['default']
    url = config.get('url')
    port = config.getint('port')
    rems_url = config.get('rems_url')
    rems_admin_userid = config.get('rems_admin_userid')
    rems_admin_api_key = config.get('rems_admin_api_key')
except KeyError as e:
    log.error(f'Configuration error: missing key {e}')
    exit(1)


class REMSAutoEntitlementRevoker(http.server.BaseHTTPRequestHandler):
    def get_entitlement_application_ids(self, user_id, resource_id, event_id):
        '''Return list of application IDs for entitlements associated with user_id and resource_id'''
        entitlements_url = f'{rems_url}/api/entitlements'
        params = {
            'user': user_id,
            'resource': resource_id,
            'expired': 'false'
        }
        headers = {
            'accept': 'application/json',
            'x-rems-api-key': rems_admin_api_key,
            'x-rems-user-id': rems_admin_userid,
        }
        log.info(f'{event_id} Retrieving entitlements for user ID {user_id} and resource ID {resource_id}')
        response = requests.get(
            url=entitlements_url,
            params=params,
            headers=headers,
        )
        log.info(f'{event_id} Response: {response.status_code} {response.reason}')
        if response.status_code != 200:
            raise Exception(f'Response code {response.status_code} received when retrieving entitlements')

        return [entitlement['application-id'] for entitlement in response.json()]

    def revoke_application(self, application_id):
        revoke_url = f'{rems_url}/api/applications/revoke'
        headers = {
            'accept': 'application/json',
            'x-rems-api-key': rems_admin_api_key,
            'x-rems-user-id': rems_admin_userid,
            'Content-Type': 'application/json',
        }
        data = json.dumps(
            {
                "application-id": application_id,
                "comment": "Application revoked by auto-revoker after user added to deny list",
                "attachments": []
            }
        )
        response = requests.post(
            url=revoke_url,
            headers=headers,
            data=data,
        )

        if response.status_code != 200:
            raise Exception(f'Response code {response.status_code} received. Reason: {response.reason}')


    def revoke_entitlements(self, user_id, resource_id, event_id):
        application_ids = self.get_entitlement_application_ids(user_id, resource_id, event_id)
        revoked_count = 0
        for application_id in application_ids:
            try:
                log.info(f'{event_id} Revoking application {application_id}')
                self.revoke_application(application_id)
                log.info(f'{event_id} Revoked application {application_id}')
                revoked_count += 1
            except Exception as e:
                log.info(f'{event_id} Failure revoking application_id {application_id}: {e}')
        return revoked_count

    def do_PUT(self):
        log.debug(f'Received PUT request, headers: {self.headers}')
        length = int(self.headers['content-length'])
        payload = self.rfile.read(length).decode("utf-8")
        data = json.loads(payload)
        event_id = f'event/id:{data["event/id"]}'
        log.debug(f'{event_id} data: {data}')
        try:
            if data['event/type'] == EVENT_TYPE:
                log.info(f'{event_id} Received event notification: {data["event/type"]}')
                # TODO: Find out what these should actually be
                user_id = data['event/blacklist']['blacklist/user']['userid']
                resource_id = data['event/blacklist']['blacklist/resource']['resource/ext-id']
                log.info(f'{event_id} Revoking entitlements for user id: {user_id}, resource_id: {resource_id}')
                try:
                    revoked_count = self.revoke_entitlements(user_id, resource_id, event_id)
                    log.info(
                        f'{event_id} Revoked {revoked_count} entitlements for user id: {user_id}, resource_id: {resource_id}')
                    self.send_response(200, message='OK')
                except Exception as e:
                    log.info(f'{event_id} Failure revoking entitlements')
                    self.send_response(400, message=f'{e}')
            else:
                log.info(f'{event_id} Received illegal event type: {data["event/type"]}. Expected {EVENT_TYPE}')
                self.send_response(400)
        except KeyError:
            msg = f'{event_id} KeyError: Missing or invalid data!'
            log.debug(msg)
            log.debug(f'{event_id} Data: {payload}')
            self.send_response(400, message=msg)
            raise
        self.end_headers()
        return


if __name__ == "__main__":
    handler_class = REMSAutoEntitlementRevoker
    http_server = http.server.HTTPServer((url, port), handler_class)
    with http_server:
        try:
            log.info(f'Event listener at \'{url}:{port}\'. Stop with [Ctrl-C].')
            http_server.serve_forever()
        except KeyboardInterrupt:
            log.info('Event listener stopped')
