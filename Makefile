.PHONY: install deploy clean help

clean:
	-rm flow-storm-server.jar
	-rm pom.xml
	-rm target -rf

flow-storm-server.jar:
	clj -A:jar flow-storm-server.jar

pom.xml:
	clj -Spom
	mvn versions:set -DnewVersion=$(version)

watch-ui:
	npx shadow-cljs watch client

watch-css:
	clj -A:garden -e "(require '[garden-watcher.core :as gw]) (require '[com.stuartsierra.component :as component]) (component/start (gw/new-garden-watcher '[flow-storm-server.styles.main]))"

release-ui: clean
	npx shadow-cljs release client

release: flow-storm-server.jar pom.xml

install: flow-storm-server.jar pom.xml
	mvn install:install-file -Dfile=flow-storm-server.jar -DpomFile=pom.xml

deploy:
	mvn deploy:deploy-file -Dfile=flow-storm-server.jar -DrepositoryId=clojars -DpomFile=pom.xml -Durl=https://clojars.org/repo

run:
	clj -m flow-storm-server.server

tag-release:
	git add CHANGELOG.md && \
	git commit -m "Updating CHANGELOG after $(version) release" && \
	git tag "v$(version)" && \
	git push origin master

help:
	@echo "For releasing to clojars run"
	@echo "make version=x.y.z release"
