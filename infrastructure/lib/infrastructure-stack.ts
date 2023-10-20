// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
import * as cdk from 'aws-cdk-lib';
import * as path from "path";
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as iot from '@aws-cdk/aws-iot-alpha';
import * as actions from '@aws-cdk/aws-iot-actions-alpha'
import * as iam from "aws-cdk-lib/aws-iam";
import * as events from "aws-cdk-lib/aws-lambda-event-sources";


export class InfrastructureStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        let account = cdk.Stack.of(this).account;
        let region = cdk.Stack.of(this).region;

        //Step 1: Simulate hourly IoT events during the month of May (720 events in total) with a Lambda
        //        These events are sent to the iot/beehive MQTT topic
        const lambdaIotPolicy = new iam.PolicyStatement({
            actions: ['iot:Publish'],
            effect: iam.Effect.ALLOW,
            resources: ['arn:aws:iot:'+region+':'+account+':topic/iot/beehive'],
        });

        const lambdaRole = new iam.Role(this, 'LambdaRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
        });
        lambdaRole.addToPolicy(lambdaIotPolicy)

        const iotBeehiveEventsSimulatorLambda = new lambda.Function(this, 'IoTBeehiveEventsSimulatorLambda', {
            code: lambda.Code.fromAsset(path.join('../iot-beehive-events-simulator-lambda/target', 'iot-beehive-events-simulator-lambda.zip')),
            runtime: lambda.Runtime.JAVA_17,
            functionName: 'IoTBeehiveEventsSimulatorLambda',
            handler: 'org.beehive.IoTBeehiveEventsSimulator',
            memorySize: 4096,
            timeout: cdk.Duration.seconds(120),
            role: lambdaRole
        });

        //Step 2: Store the IoT MQTT Events in an SQS Queue by using an IoT Topic Rule
        //        This allows the IoT Events to be processed in an asynchronous manner

        const ioTEventsDeadLetterSQSQueue = new sqs.Queue(this, 'IoTEventsDeadLetterSQSQueue', {
            queueName: 'iot-events-dead-letter',
            enforceSSL : true
        });

        const iotEventsSQSQueue = new sqs.Queue(this, 'IoTEventsSQSQueue', {
            visibilityTimeout: cdk.Duration.seconds(120),
            queueName: 'iot-events',
            enforceSSL : true,
            deadLetterQueue : {
                queue: ioTEventsDeadLetterSQSQueue,
                maxReceiveCount : 3
            }
        });

        new iot.TopicRule(this, 'IoTEventsSQSQueueRule', {
            topicRuleName: 'IoTEventsSQSQueueRule',
            description: 'invokes the lambda function',
            sql: iot.IotSql.fromStringAsVer20160323("SELECT * FROM 'iot/beehive'"),
            actions: [new actions.SqsQueueAction(iotEventsSQSQueue)],
        });

        //Step 3: Persist the IoT Events to a DynamoDB table using a Lambda.
        //      i)  This allows the IoT Events to be read in *order* (very important for the anomaly detection)
        //      ii) This allows the IoT Event item to be updated with an anomaly score.

        const lambdaProcessingPolicy = new iam.PolicyStatement({
            actions: ['sqs:ReceiveMessage'],
            effect: iam.Effect.ALLOW,
            resources: ['arn:aws:sqs:'+region+':'+account+':iot-events'],
        });

        const lambdaProcessingRole = new iam.Role(this, 'LambdaProcessingRole', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
        });
        lambdaProcessingRole.addToPolicy(lambdaProcessingPolicy)

        const ioTEventPersistenceLambda = new lambda.Function(this, 'IoTEventPersistenceLambda', {
            code: lambda.Code.fromAsset(path.join('../iot-event-persistence-lambda/target', 'iot-event-persistence-lambda.zip')),
            runtime: lambda.Runtime.JAVA_17,
            functionName: 'IoTEventPersistenceLambda',
            handler: 'org.beehive.IoTEventPersistenceLambda',
            memorySize: 4096,
            timeout: cdk.Duration.seconds(120),
            role: lambdaProcessingRole
        });

        const table = new dynamodb.Table(this, 'HiveEventsTable', {
            tableName: 'HIVE_EVENTS',
            partitionKey: {
                name: 'hive_id',
                type: dynamodb.AttributeType.STRING
            },
            sortKey: {
                name: "date_time",
                type: dynamodb.AttributeType.STRING
            },
            removalPolicy: cdk.RemovalPolicy.DESTROY,
            pointInTimeRecovery: true
        });

        const eventSource = new events.SqsEventSource(iotEventsSQSQueue);
        ioTEventPersistenceLambda.addEventSource(eventSource);

        table.grantReadWriteData(ioTEventPersistenceLambda)

        //Step 4: Finally the events can be read and processed using a Lambda.

        const iotAnomalyDetectionLambda = new lambda.Function(this, 'IoTAnomalyDetectionLambda', {
            code: lambda.Code.fromAsset(path.join('../iot-anomaly-detection-lambda/target', 'iot-anomaly-detection-lambda.zip')),
            runtime: lambda.Runtime.JAVA_17,
            functionName: 'IoTAnomalyDetectionLambda',
            handler: 'org.beehive.IoTAnomalyDetectionLambda',
            memorySize: 4096,
            timeout: cdk.Duration.seconds(120),
            reservedConcurrentExecutions: 1,
            role: lambdaProcessingRole
        });

        table.grantReadWriteData(iotAnomalyDetectionLambda)

    }
}
