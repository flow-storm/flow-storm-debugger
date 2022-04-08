.PHONY: 

clean:
	-rm flow-storm-dbg.jar
	-rm flow-storm-inst.jar
	-rm pom.xml

lint-dbg:
	clj-kondo --config .clj-kondo/config.edn --lint src-dbg src-shared

lint-inst:
	clj-kondo --config .clj-kondo/config.edn --lint src-inst src-shared

flow-storm-dbg.jar:
	clj -X:build hf.depstar/jar :jar flow-storm-dbg.jar :aliases '[:dbg]' :paths-only false :sync-pom true :version '"$(VERSION)"' :group-id com.github.jpmonettas :artifact-id flow-storm-dbg;

flow-storm-inst.jar:
	clj -X:build hf.depstar/jar :jar flow-storm-inst.jar :aliases '[:inst]' :sync-pom true :version '"$(VERSION)"' :group-id com.github.jpmonettas :artifact-id flow-storm-inst;

install-dbg: flow-storm-dbg.jar
	mvn install:install-file -Dfile=flow-storm-dbg.jar -DpomFile=pom.xml

install-inst: flow-storm-inst.jar
	mvn install:install-file -Dfile=flow-storm-inst.jar -DpomFile=pom.xml

deploy-dbg:
	mvn deploy:deploy-file -Dfile=flow-storm-dbg.jar -DrepositoryId=clojars -DpomFile=pom.xml -Durl=https://clojars.org/repo

deploy-inst:
	mvn deploy:deploy-file -Dfile=flow-storm-inst.jar -DrepositoryId=clojars -DpomFile=pom.xml -Durl=https://clojars.org/repo
