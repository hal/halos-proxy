FROM jboss/wildfly

RUN $JBOSS_HOME/bin/add-user.sh -u admin -p admin --silent
EXPOSE 9990

CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-bmanagement", "0.0.0.0"]
