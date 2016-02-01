FROM 162510209540.dkr.ecr.us-east-1.amazonaws.com/postgres94

ENV PGPASSWORD docker

ADD init-db.sh /tmp/init-db.sh
CHMOD 700 /tmp/init-db.sh

RUN /etc/my_runalways/startup-postgresql &&\
    /tmp/init-db.sh 0.0.0.0 &&\
    /etc/init.d/postgresql stop && \
    rm -f /tmp/*gz

