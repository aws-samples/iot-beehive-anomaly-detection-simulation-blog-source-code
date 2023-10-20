#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { InfrastructureStack } from '../lib/infrastructure-stack';
import {Aspects} from "aws-cdk-lib";
import {AwsSolutionsChecks} from "cdk-nag";

const app = new cdk.App();
Aspects.of(app).add(new AwsSolutionsChecks({ verbose: true }))

new InfrastructureStack(app, 'Beehive Anomaly Detection Simulation Stack', {
    stackName: 'beehive-anomaly-detection-simulation-stack'
});