# Auto Entitlement Revoker

A simple HTTP server listening REMS' `blacklist.event/add` event notifications.
On notification, it tries to revoke any applications associated with the new blacklist entry.

## Pre-requirements for REMS
You need the "bona fide status" catalogue item. For this you first need to create the following objects. These can be 
created in REMS UI except where stated otherwise.
* (Set up `owner` role for yourself)
* (Create an API key)
* Create an organisation
* Create the `auto-entitlement-revoker-bot` user (create using API/swagger)
* Create a resource
* Create a form (only a single email field required)
* Create a workflow (with `auto-entitlement-revoker-bot` as the handler)
* Create a catalogue item

For more info see [Bona Fide bot documentation](../../../docs/bots.md#bona-fide-bot)

## Installation

See [config.ini](config.ini) for an example of a configuration file that must be supplied. 

Add this in your REMS `config.edn`:
```
:event-notification-targets [{:url "http://127.0.0.1:3009"
                              :event-types [:blacklist.event/add]}]
```

## Running locally
You can test your installation locally. Pick some `<BUILD_NAME>` and `<CONTAINER_NAME>` and run:
```
cd rems/resources/addons/auto_entitlement_revoker
docker build -t <BUILD_NAME> .
docker run --rm --network="host" --name <CONTAINER_NAME> <BUILD_NAME>
```
Invoke the `blacklist.event/add` event in REMS and check that the auto_entitlement_revoker log looks ok. The actual request to 
REMS might fail from your local environment depending your configuration. 
