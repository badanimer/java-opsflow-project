# 03 Spring Boot Project Setup

## Understanding To Build

The project structure determines whether the code becomes maintainable or messy. Today is not only about making Spring Boot run. You need to understand why backend projects are layered, why DTO, Entity, and VO should not be mixed randomly, and why global exception handling and unified API responses should be designed early.

## Questions To Consider

Start with package structure. The controller receives requests. The service contains business logic. The repository or mapper talks to the database. The entity maps database tables. DTOs receive request data. VOs shape response data. Avoid putting all business logic inside controllers.

Then think about technology choice. JPA and MyBatis Plus are both acceptable. JPA focuses more on object-relational mapping. MyBatis Plus gives you more direct control over SQL-like operations. Choose one that you are willing to learn, but be able to explain why.

Also think about configuration. Database URLs, Redis URLs, and JWT secrets should not be hard-coded. Understand application.yml, environment variables, and different environment profiles.

## What To Research

Useful keywords:

- Spring Boot layered architecture
- Spring Boot DTO VO Entity difference
- Spring Boot global exception handler
- Spring Boot response wrapper best practice
- Spring Boot application.yml profile

Focus on why these structures exist, not only on copying a folder layout.

## How To Ask AI

Ask:

"I am setting up a Spring Boot order and inventory system. Please design a package structure that is suitable for a junior portfolio project but still looks professional. Explain what belongs in each layer and what should not be placed there."

Or send your folder structure:

"Here is my Spring Boot project structure. Please review whether the layering is clear, whether naming is understandable, and whether anything will become hard to extend later."

## Expected Output

You should have a backend project that can start successfully and includes:

- A health check endpoint
- Database connection configuration
- A unified response object
- A first version of global exception handling
- Swagger or Knife4j page
- A clear base package structure

You do not need many features yet. The foundation should be stable.

## Review Questions

- How much logic should be inside a controller?
- What problems appear if DTO and Entity are mixed?
- Why design unified responses and exception handling early?
- Can someone else open my project and understand the structure quickly?
