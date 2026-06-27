# Superset author-UI JWT proxy — a thin nginx reverse proxy that replaces the
# Istio ext_authz path (which needs mesh-admin meshConfig rights we don't have).
# Same base as the project frontends (FROM nginx:alpine), pushed to the project
# nexus repo so the cluster can pull it without dockerhub access.
#
# The stock nginx entrypoint runs envsubst on /etc/nginx/templates/*.template at
# startup, rendering ${INGRESS_HOST} / ${POD_NAMESPACE} into conf.d/default.conf.
FROM nginx:alpine
COPY default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 8080
