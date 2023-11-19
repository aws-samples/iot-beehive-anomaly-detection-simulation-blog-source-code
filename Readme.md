# Serverless anomaly detection in bee hives IoT events with AWS Lambda and CDK Source Code
This is an accompaniment to the [Blog article](https://aws.amazon.com/blogs/iot/serverless-iot-anomaly-detection/) and contains a hands-on example of how to create and test your own serverless anomaly detector with AWS IoT Core, AWS Lambda and CDK. In the simulation scenario IoT events containing the simulated weight of a beehive are sent to AWS IoT Core. An anomaly detector running on AWS Lambda generates an anomaly score for each event. IoT events are persisted to Amazon DynamoDB.

#### Simulation architecture
![simulation-architecture.png](architecture%2Fsimulation-architecture.png)
#### Prerequisites

- An AWS account
- AWS Cloud Development Kit (AWS CDK)
- Java 17,  Maven, git, npm

This simulation was tested using macOS Ventura 13.5 will the following software versions.

``` bash
java -version
openjdk version "17.0.7" 2023-04-18 LTS
OpenJDK Runtime Environment Corretto-17.0.7.7.1 (build 17.0.7+7-LTS)
OpenJDK 64-Bit Server VM Corretto-17.0.7.7.1 (build 17.0.7+7-LTS, mixed mode, sharing)
```

``` bash
mvn --version
Apache Maven 3.9.0 (9b58d2bad23a66be161c4664ef21ce219c2c8584)
```

``` bash
git --version
git version 2.39.2 (Apple Git-143)
```

``` bash
cdk --version
2.87.0 (build 9fca790)
```

``` bash
npm --version
8.19.2
```


#### Clone the project
```bash
git clone git@ssh.gitlab.aws.dev:nashke/beehive-anomaly-detection-simulation.git
cd beehive-anomaly-detection-simulation
```

#### Compile the AWS Lambda source code
```bash
mvn clean install
```

#### Deploy infrastructure with CDK to the following AWS Services : Amazon API Gateway, Amazon DynamoDB and AWS Lambda 
```bash
cd ../infrastructure 
npm ci

# If running cdk for the first time you will need to bootstrap the account.
# See https://docs.aws.amazon.com/cdk/v2/guide/bootstrapping.html for more details
cdk boostrap 

# Confirm IAM Statement changes by selecting y and enter to the following question.
# "Do you wish to deploy these changes (y/n)?" 
cdk deploy
```

#### Run the anomaly detection simulation

The test data is located in the following file: iot-beehive-events-simulator-lambda/src/main/resources/hive-sample-events.json It contains 720 (30 days by 24 hours) events, only one of which (event 173) is detected as anomalous, it is approximately 1.5Kg below its expected value. Load the these IoT Events by running the following Lambda

```bash
aws lambda invoke --function-name IoTBeehiveEventsSimulatorLambda --cli-binary-format raw-in-base64-out --payload '{"hiveID":"1"}' response.json
```

Confirm that 720 events have been loaded to DynamoDB
```bash
aws dynamodb scan --table-name HIVE_EVENTS --select "COUNT"
```

```json
{
    "Count": 720,
    "ScannedCount": 720,
    "ConsumedCapacity": null
}
```

Now launch the anomaly detection by executing the following Lambda.

```bash
aws lambda invoke --function-name IoTAnomalyDetectionLambda --cli-binary-format raw-in-base64-out --payload '{"hiveID": "1"}' response.json
less response.json | jq
```

Only one event is detected as anomalous. 

```json
[
  {
    "datetime": "2023-05-08 04:00:00.0 +0200",
    "weight": 64650,
    "anomalyGrade": 1.0,
    "anomalyScore": 1.2257463093204803,
    "expectedValue": 66195,
    "isEventAnomalous": true
  }
]
```

Alternatively you can scan the DynamoDB for anomalous events.
```bash
aws dynamodb scan --table-name HIVE_EVENTS --filter-expression " is_event_anomalous = :is_event_anomalous" --expression-attribute-values '{":is_event_anomalous":{"S":"true"}}'
```

#### Cleanup

```bash
cd ../infrastructure
# Approve the infrastructure teardown by selecting y and enter to the following question
# "Are you sure you want to delete: InfrastructureStack (y/n)?" 
cdk destroy
```