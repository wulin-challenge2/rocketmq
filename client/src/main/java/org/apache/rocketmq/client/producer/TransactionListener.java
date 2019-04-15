/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.producer;

import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 事务监听器,主要定义实现本地事务状态执行、本地事务状态回查两个接口.
 *
 */
public interface TransactionListener {
    /**
     * When send transactional prepare(half) message succeed, this method will be invoked to execute local transaction.
     * 
     * <p> 当发送事务 prepare(half)消息成功时，将调用此方法来执行本地事务。
     *
     * @param msg Half(prepare) message - 一半（准备）消息
     * @param arg Custom business parameter - 自定义业务参数
     * @return Transaction state - 事务状态
     */
    LocalTransactionState executeLocalTransaction(final Message msg, final Object arg);

    /**
     * When no response to prepare(half) message. broker will send check message to check the transaction status, and this
     * method will be invoked to get local transaction status.
     * 
     * <p> 当准prepare(half)消息没有响应。 broker将发送检查消息以检查事务状态，并将调用此方法以获取本地事务状态。
     *
     * @param msg Check message - 要检测的消息
     * @return Transaction state - 事务状态
     */
    LocalTransactionState checkLocalTransaction(final MessageExt msg);
}