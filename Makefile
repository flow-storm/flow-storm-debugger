.PHONY: clean test lint-dbg lint-inst install-dbg install-inst deploy-dbg deploy-inst

clean:
	clj -T:build clean

test:
	clj -M:test:inst

lint:
	clj-kondo --config .clj-kondo/config.edn --lint src-dbg src-shared src-inst

flow-storm-dbg.jar:
	clj -T:build jar-dbg

flow-storm-inst.jar:
	clj -T:build jar-inst

install-dbg: flow-storm-dbg.jar
	mvn install:install-file -Dfile=target/flow-storm-dbg.jar -DpomFile=target/classes/META-INF/maven/com.github.jpmonettas/flow-storm-dbg/pom.xml

install-inst: flow-storm-inst.jar
	mvn install:install-file -Dfile=target/flow-storm-inst.jar -DpomFile=target/classes/META-INF/maven/com.github.jpmonettas/flow-storm-inst/pom.xml

deploy-dbg:
	mvn deploy:deploy-file -Dfile=target/flow-storm-dbg.jar -DrepositoryId=clojars -DpomFile=target/classes/META-INF/maven/com.github.jpmonettas/flow-storm-dbg/pom.xml -Durl=https://clojars.org/repo

deploy-inst:
	mvn deploy:deploy-file -Dfile=target/flow-storm-inst.jar -DrepositoryId=clojars -DpomFile=target/classes/META-INF/maven/com.github.jpmonettas/flow-storm-inst/pom.xml -Durl=https://clojars.org/repo
