export PGPASSWORD=docker &&\
psql -U docker -d postgres -h $1 --command "CREATE USER akkapg WITH SUPERUSER PASSWORD 'akkapg';" &&\
createdb -E UTF8 -U docker -h $1 -O akkapg akkapg --template template0  &&\
psql -U docker -d akkapg -h $1 --command "create EXTENSION IF NOT EXISTS hstore;"