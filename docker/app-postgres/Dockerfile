FROM 162510209540.dkr.ecr.us-east-1.amazonaws.com/postgres94

ENV PGPASSWORD docker

RUN /etc/my_runalways/startup-postgresql &&\
    psql -U docker -d postgres -h 0.0.0.0 --command "CREATE USER akkapg WITH SUPERUSER PASSWORD 'akkapg';" &&\
    createdb -E UTF8 -U docker -h 0.0.0.0 -O akkapg akkapg --template template0  &&\
    export PGPASSWORD=akkapag
    psql -U akkapg -d akkapg -h 0.0.0.0 --command "create EXTENSION IF NOT EXISTS hstore;" &&\
    /etc/init.d/postgresql stop && \
    rm -f /tmp/*gz

