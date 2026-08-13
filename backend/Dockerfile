# ============================================================
# Dockerfile for Spring Boot Backend
# ============================================================
# This file tells Docker how to build a container image
# for our Java backend. Think of it like a recipe.
#
# We use a TWO-STAGE build:
#   Stage 1 (build)  → downloads dependencies + compiles Java code
#   Stage 2 (run)    → takes only the final .jar and runs it
#
# Why two stages? The build tools (Maven, source code) are huge.
# We don't need them at runtime. Two stages keep the final
# image small and fast.
# ============================================================


# ---- STAGE 1: BUILD ----
# Start from an image that has Java 21 + Maven pre-installed
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the container
# (like doing "cd /app" inside a virtual machine)
WORKDIR /app

# Copy ONLY the pom.xml first.
# Docker caches each step. If pom.xml hasn't changed, Docker
# will skip re-downloading all dependencies → saves time!
COPY pom.xml .

# Download all dependencies (without compiling code yet).
# -q = quiet mode, less log output
RUN mvn dependency:go-offline -q

# Now copy the rest of the source code
COPY src ./src

# Compile and package into a .jar file.
# -DskipTests → don't run tests during Docker build (faster).
# The .jar file lands in /app/target/
RUN mvn clean package -DskipTests -q


# ---- STAGE 2: RUN ----
# Start fresh from a slim Java-only image (no Maven needed)
FROM eclipse-temurin:21-jre

# Set the working directory
WORKDIR /app

# Copy the .jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Tell Docker this container will listen on port 8081
# (This is just documentation; actual port mapping is in docker-compose)
EXPOSE 8081

# The command to run when the container starts.
# We disable the "local" profile and pass database URL etc. through
# environment variables (set in docker-compose.yml).
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
