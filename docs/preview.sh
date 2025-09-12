#!/bin/sh
docker run --rm -p 4000:4000 -v "$PWD/docs":/srv/jekyll -w /srv/jekyll jekyll/jekyll:4 \
  bash -lc "bundle install && jekyll serve --livereload --host localhost"
