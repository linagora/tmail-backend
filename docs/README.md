# Team-mail documentation website how to

## Build

You need [Antora](https://antora.org/) installed locally.

Install [NPM](https://www.npmjs.com/) first, then install Antora:

```
npm i -g @antora/cli@2.3 @antora/site-generator-default@2.3
```

To build the documentation website run in this folder:

```
antora antora-playbook-local.yml --stacktrace
```