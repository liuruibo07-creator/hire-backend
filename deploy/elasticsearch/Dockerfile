FROM docker.elastic.co/elasticsearch/elasticsearch:7.12.1
ARG IK_PLUGIN_URL=https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-7.12.1.zip
RUN bin/elasticsearch-plugin install --batch "${IK_PLUGIN_URL}"
