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
package org.apache.rocketmq.broker.transaction;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.header.EndTransactionRequestHeader;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.PutMessageResult;

public interface TransactionalMessageService {

    /**
     * Process prepare message, in common, we should put this message to storage service.
     *
     * @param messageInner Prepare(Half) message.
     * @return Prepare message storage result.
     */
    PutMessageResult prepareMessage(MessageExtBrokerInner messageInner);

    /**
     * Delete prepare message when this message has been committed or rolled back.
     *
     * @param messageExt
     */
    boolean deletePrepareMessage(MessageExt messageExt);

    /**
     * Invoked to process commit prepare message.
     *
     * @param requestHeader Commit message request header.
     * @return Operate result contains prepare message and relative error code.
     */
    OperationResult commitMessage(EndTransactionRequestHeader requestHeader);

    /**
     * Invoked to roll back prepare message.
     *
     * @param requestHeader Prepare message request header.
     * @return Operate result contains prepare message and relative error code.
     */
    OperationResult rollbackMessage(EndTransactionRequestHeader requestHeader);

    /**
     * Traverse uncommitted/unroll back half message and send check back request to producer to obtain transaction
     * status.
     * 
     * 通过未提交/未回滚的后半部分消息向生产者发送请求获得事务状态
     *
     * @param transactionTimeout The minimum time of the transactional message to be checked firstly, one message only
     * exceed this time interval that can be checked.
     * 
     * <p> 首先,事务消息将在最小时间内被检测,但该事务消息只有超过这个时间间隔才能被检测
     * 
     * @param transactionCheckMax The maximum number of times the message was checked, if exceed this value, this
     * message will be discarded.
     * 
     * <p> 事务消息检测的最大次数,如果超过这个值,该消息将被丢弃
     * 
     * @param listener When the message is considered to be checked or discarded, the relative method of this class will
     * be invoked.
     * 
     * <p> 当一个消息被认为将要检测或者丢弃,这个类的相关方法将会被调用
     * 
     * <p> 注意: 当想要让一个事务消息发送成功后立即回调检测方法,可以多个设置 transactionTimeOut: 事务过期时间, 
     * transactionCheckInterval : 事务检测时间间隔, 或者构建事务消息时设置属性
     * MessageConst.PROPERTY_CHECK_IMMUNITY_TIME_IN_SECONDS,该属性的意思是在设置时间内该定时器不会向生产者发送检测请求
     */
    void check(long transactionTimeout, int transactionCheckMax, AbstractTransactionalMessageCheckListener listener);

    /**
     * Open transaction service.
     *
     * @return If open success, return true.
     */
    boolean open();

    /**
     * Close transaction service.
     */
    void close();
}
