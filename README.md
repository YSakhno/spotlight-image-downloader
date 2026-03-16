# Spotlight Image Downloader

This is a command-line utility for scraping and downloading Spotlight images from a website. It automatically traverses
the provided initial URL to discover various image categories, downloads the images into a local directory, and
maintains a small local SQLite database to track downloaded files and prevent duplicates in subsequent runs.

For each category, the utility also generates a CSV file containing metadata for each downloaded image, including its
filename, title, and description, making it easy to organize and reference your local collection.


## Command Line Parameters

**Usage pattern:**

    spotlight-image-downloader [<options>] <initial-url>

The utility accepts the following command-line argument and options:

- `initial-url`: **(Required)** The starting URL for the scraping process. This page should contain the list of all
  categories rather than a specific category's images.  
  Note: this is a positional argument rather than an option.
- `--version`: Displays the application name and current version, then exits.
- `-h`, `--help`: Displays a help message showing the available parameters and options.

### Example Usage

```bash
./gradlew shadowRun --args="https://example.link/spotlight-categories"
```


## Building and Running from Source

### Prerequisites

To build and run this project, you need **Java Development Kit (JDK) 21** or higher.

- **Download JDK 21:** You can download it from
  [Adoptium (Eclipse Temurin)](https://adoptium.net/temurin/releases/?version=21) or use your preferred package manager
- (e.g., `brew install openjdk@21`, `sudo apt install openjdk-21-jdk`).

### Building the Project

The project uses Gradle as its build system. You can build the project and create an executable “shadow” JAR (uber-JAR)
using the following command:

```bash
./gradlew shadowJar
```

This will create a JAR file in the `build/libs` directory containing all the necessary dependencies.

### Running from Source

You can run the application directly through Gradle:

```bash
./gradlew runShadow --args="<initial-url>"
```

Alternatively, after building the shadow JAR, you can run it using the `java` command:

```bash
java -jar build/libs/spotlight-image-downloader-1.0-SNAPSHOT-all.jar https://url.of/initial/page
```

*(Note: The exact JAR filename might vary based on the version specified in the build script.)*
