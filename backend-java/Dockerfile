# Imagen base con JDK 17 (compatible con ICE)
FROM openjdk:17-slim

# Instalar dependencias necesarias
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    gnupg \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Instalar Ice 3.7.9
RUN curl -s https://zeroc.com/download/ice/3.7/ubuntu2210/zeroc-ice37.key | apt-key add - && \
    echo "deb https://zeroc.com/download/ice/3.7/ubuntu2210 stable main" > /etc/apt/sources.list.d/zeroc-ice.list

RUN apt-get update && apt-get install -y \
    ice-all-runtime=3.7.9-1 \
    ice-all-dev=3.7.9-1 \
    && rm -rf /var/lib/apt/lists/*

# Crear directorio de la app
WORKDIR /app

# Copiar archivos Gradle y fuente
COPY . .

# Dar permisos al gradlew
RUN chmod +x ./gradlew

# Construir el proyecto
RUN ./gradlew build --no-daemon

# Exponer puertos del servidor
EXPOSE 6789
EXPOSE 10000

# Ejecutar servidor Java (jar generado: chat-server.jar)
CMD ["java", "-jar", "build/libs/chat-server.jar"]