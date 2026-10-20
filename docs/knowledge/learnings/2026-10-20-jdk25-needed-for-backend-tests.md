tags: [build, jdk, verify]
The default machine JDK was 17; the project needs 25 and surefire fails with class file version 69.0.
Reviewers must run backend commands under a JDK 25 environment, or QA reports a false failure.
