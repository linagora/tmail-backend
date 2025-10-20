#!/bin/bash

set -euo pipefail

docker_check() {
    echo "Checking if docker is installed on your machine..."
    if command -v docker >/dev/null 2>&1; then
        echo "Docker is installed! Proceeding..."
    else
        echo "Docker is not installed. Please install docker (https://docs.docker.com/engine/install/) to continue." >&2
        exit 1
    fi
}

start_services() {
    echo "Pulling latest images..."
    docker compose pull || echo "Pulling latest images fail"
    echo "Starting services..."
    docker compose up -d
    retvar=$?
    if [[ retvar -eq 0 ]]; then
        echo
        echo "Please add the following line to your '/etc/hosts' file"
        echo
        echo "127.0.0.1 api.manager.example.com manager.sso.example.com sso.example.com handler.sso.example.com test.sso.example.com tmail-backend"
        echo

        echo "All service started!"
        echo "LemonLDAP can be reached at http://sso.example.com"
        echo "Login credentials are 'dwho / dwho' for 'Demo' method and 'james-user@tmail.com / secret' for 'LDAP' method"
        echo

        echo "For more information, check the README.md"
    else
        echo "There is a problem starting your services. Check the previous lines on your terminal for logs"
        exit 2
    fi
}

stop_services() {
    echo "Stopping all services..."
    docker compose down
}

help() {
    echo "Usage: ./dev.sh (start | stop)"
    echo
    echo "  start:   Start all services (james, krakend, lemonldap, openldap)"
    echo "  stop:    Stop all services"
}

if [[ $# -ne 1 ]]; then
    help
else
    case $1 in
    start)
        docker_check
        start_services
        ;;
    stop)
        docker_check
        stop_services
        ;;
    *)
        help
        ;;
    esac
fi
