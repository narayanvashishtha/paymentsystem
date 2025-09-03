# Step 1: Use official JDK image
FROM openjdk:21-jdk-slim

# Step 2: Set working directory
WORKDIR /app

# Step 3: Copy jar file (will be created after mvn package)
COPY target/paymentsystem-0.0.1-SNAPSHOT.jar app.jar

# Step 4: Run the jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
