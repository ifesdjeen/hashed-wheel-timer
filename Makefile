BENCHMARKS_JAR := bench/target/benchmarks.jar

.PHONY: $(BENCHMARKS_JAR)
$(BENCHMARKS_JAR):
	mvn package -DskipTests

benchmark: $(BENCHMARKS_JAR)
	java -jar $(BENCHMARKS_JAR)
