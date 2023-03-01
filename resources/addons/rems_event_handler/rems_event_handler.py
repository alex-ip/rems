#!/usr/bin/env python3

# A simple HTTP server listening for REMS event notifications.
# On 'application.event/revoked' notification it tries to revoke any existing entitlements for that user & resource.

# A configuration file 'config.ini' must be supplied.

# Usage: ./auto_entitlement_revoker.py
#        Stop with Ctrl-C

import configparser
import http.server
import json
import logging

import requests

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


def revoke_application(application_id):
    """Revoke application specified by application_id"""
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


def get_entitlement_application_ids(user_id, resource_id, event_id):
    """Return list of application IDs for entitlements associated with user_id and resource_id"""
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


def revoke_entitlements(user_id, resource_id, event_id):
    """
    Revoke applications for active entitlements for specified user_id and resource_id
    Will report errors and continue processing
    """
    application_ids = get_entitlement_application_ids(user_id, resource_id, event_id)
    revoked_count = 0
    # Only revoke the first application. The event handler should be called recursively
    if application_ids:
        application_id = application_ids[0]
        try:
            log.info(f'{event_id} Revoking application {application_id}')
            revoke_application(application_id)
            log.info(f'{event_id} Revoked application {application_id}')
            revoked_count += 1
        except Exception as e:
            log.warning(f'{event_id} Failure revoking application_id {application_id}: {e}')
    return revoked_count


def application_revoked_event_handler(data, event_id):
    """Handle application.event/revoked event - added to REMSEventHandler.EVENT_HANDLERS"""

    # Pull required information from data structure in request body
    user_id = data['event/application']['application/applicant']['userid']
    resource_id = data['event/application']['application/resources'][0]['resource/ext-id']
    log.info(f'{event_id} Revoking entitlements for user id: {user_id}, resource_id: {resource_id}')

    revoked_count = revoke_entitlements(user_id, resource_id, event_id)
    log.info(
        f'{event_id} Revoked {revoked_count} entitlements for user id: {user_id}, resource_id: {resource_id}')


class REMSEventHandler(http.server.BaseHTTPRequestHandler):
    # Specify valid events and their handler functions here
    EVENT_HANDLERS = {'application.event/revoked': application_revoked_event_handler}

    def do_PUT(self):
        """Handle PUT request to /event path for specific events defined in REMSEventHandler.EVENT_HANDLERS"""
        log.debug(f'Received PUT request at {self.path}, headers: {self.headers}')
        length = int(self.headers['content-length'])
        payload = self.rfile.read(length).decode("utf-8")

        try:
            data = json.loads(payload)
        except Exception as e:
            msg = f'Unable to parse JSON payload! {type(e).__name__}: {e}'
            log.error(msg)
            log.debug(f'payload: {payload}')
            self.send_response(400, message=msg)
            self.end_headers()
            return

        try:
            event_id = f'event/id:{data["event/id"]}'
            log.debug(f'{event_id} data: {data}')
        except KeyError:
            msg = f'KeyError: Missing or invalid event_id!'
            log.error(msg)
            self.send_response(400, message=msg)
            self.end_headers()
            return

        if self.path != '/event':
            msg = f'{event_id} Invalid path "{self.path}"!'
            log.error(msg)
            self.send_response(404, message=msg)
            self.end_headers()
            return

        event_type = data.get('event/type') or '<UNDEFINED>'
        try:
            event_handler = REMSEventHandler.EVENT_HANDLERS.get(event_type)
            if event_handler:
                log.info(f'{event_id} Received valid event notification: {event_type}')
                event_handler(data, event_id)
                self.send_response(200, message='OK')
            else:
                msg = f'{event_id} Received illegal event type: {event_type}. ' \
                      f'Expected one of {list(REMSEventHandler.EVENT_HANDLERS.keys())}'
                log.error(msg)
                self.send_response(400, message=msg)

        except Exception as e:  # Generic exception handling for event handling
            msg = f'{event_id} Error handling event event_type: {type(e).__name__}: {e}'
            log.error(msg)
            self.send_response(500, message=msg)

        self.end_headers()
        return


if __name__ == "__main__":
    handler_class = REMSEventHandler
    http_server = http.server.HTTPServer((url, port), handler_class)
    with http_server:
        try:
            log.info(f'Event listener at \'{url}:{port}\'. Stop with [Ctrl-C].')
            http_server.serve_forever()
        except KeyboardInterrupt:
            log.info('Event listener stopped')
