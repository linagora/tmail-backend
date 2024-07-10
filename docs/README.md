# Twake Mail documentation website how to

## Build

You need [Antora](https://antora.org/) installed locally.

Install latest lts [Node](https://www.npmjs.com/) first, then install Antora:

```
npm i -g @antora/cli@3.1 @antora/site-generator@3.1
```

To build the documentation website run in this folder:

```
antora antora-playbook-local.yml --stacktrace
```