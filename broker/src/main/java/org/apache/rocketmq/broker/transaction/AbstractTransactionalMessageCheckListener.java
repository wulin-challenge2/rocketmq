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

import io.netty.channel.Channel;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.header.CheckTransactionStateRequestHeader;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractTransactionalMessageCheckListener {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getLogger(LoggerName.TRANSACTION_LOGGER_NAME);

    private BrokerController brokerController;

    private static ExecutorService executorService = new ThreadPoolExecutor(2, 5, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2000), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("Transaction-msg-check-thread");
            return thread;
        }
    });

    public AbstractTransactionalMessageCheckListener() {
    }

    public AbstractTransactionalMessageCheckListener(BrokerController brokerController) {
        this.brokerController = brokerController;
    }

    /**
     * <p> 1. 首先构建事务状态回查请求消息,核心参数包含消息offsetld、消息ID(索引)、消息事务ID、事务消息队列中的偏移量、消息主题、消息队列.
     * 然后根据消息的生产者组,从中随机选择一个消息发送者.最后向消息发送者发送事务回查命令.
     * 
     * <p> 2. 事务回查命令的最终处理者为ClientRemotingProssor的processRequest方法,最终将任务提交到TransactionMQProducer的
     * 线程池中执行,最终调用应用程序实现的TransactionListener的checkLoca!Transaction方法,返回事务状态.
     * 如果事务状态为LocalTransactionState#COMMIT_MESSAGE,则向消息服务器发送提交事务消息命令；
     * 如果事务状态为Loca!TransactionState#ROLLBACKMESSAGE,则向Broker服务器发送回滚事务操作；如果事务状态为UNOWN,
     * 则服务端会忽略此次提交.
     * @param msgExt
     * @throws Exception
     */
    public void sendCheckMessage(MessageExt msgExt) throws Exception {
        CheckTransactionStateRequestHeader checkTransactionStateRequestHeader = new CheckTransactionStateRequestHeader();
        checkTransactionStateRequestHeader.setCommitLogOffset(msgExt.getCommitLogOffset());
        checkTransactionStateRequestHeader.setOffsetMsgId(msgExt.getMsgId());
        checkTransactionStateRequestHeader.setMsgId(msgExt.getUserProperty(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX));
        checkTransactionStateRequestHeader.setTransactionId(checkTransactionStateRequestHeader.getMsgId());
        checkTransactionStateRequestHeader.setTranStateTableOffset(msgExt.getQueueOffset());
        msgExt.setTopic(msgExt.getUserProperty(MessageConst.PROPERTY_REAL_TOPIC));
        msgExt.setQueueId(Integer.parseInt(msgExt.getUserProperty(MessageConst.PROPERTY_REAL_QUEUE_ID)));
        msgExt.setStoreSize(0);
        String groupId = msgExt.getProperty(MessageConst.PROPERTY_PRODUCER_GROUP);
        Channel channel = brokerController.getProducerManager().getAvaliableChannel(groupId);
        if (channel != null) {
            brokerController.getBroker2Client().checkProducerTransactionState(groupId, channel, checkTransactionStateRequestHeader, msgExt);
        } else {
            LOGGER.warn("Check transaction failed, channel is null. groupId={}", groupId);
        }
    }

    /**
     * 发送具体的事务回查命令,使用线程池来异步发送回查消息,为了回查消费进度保存的简化,只要发送了回查消息,当前回查进度会向前推动,
     * 如果回查失败,上一步骤新增的消息将可以再次发送回查消息,那如果回查消息发送成功,会不会下一次又重复发送回查消息呢？
     * 这个可以根据OP队列中的消息来判断是否重复,如果回查消息发送成功并且消息服务器完成提交或回滚操作,这条消息会发送到OP队列中,
     * 然后首先会通过fil!OpRemoveMap根据处理进度获取一批已处理的消息,来与消息判断是否重复,由于fillopRemoveMap一次只拉32条消息,
     * 那又如何保证一定能拉取到与当前消息的处理记录呢？如果此批消息最后一条未超过事务延迟消息,则继续拉取更多消息进行判断,
     * OP队列也会随着回查进度的推进而推进.
     * @param msgExt
     */
    public void resolveHalfMsg(final MessageExt msgExt) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    sendCheckMessage(msgExt);
                } catch (Exception e) {
                    LOGGER.error("Send check message error!", e);
                }
            }
        });
    }

    public BrokerController getBrokerController() {
        return brokerController;
    }

    public void shutDown() {
        executorService.shutdown();
    }

    /**
     * Inject brokerController for this listener
     *
     * @param brokerController
     */
    public void setBrokerController(BrokerController brokerController) {
        this.brokerController = brokerController;
    }

    /**
     * In order to avoid check back unlimited, we will discard the message that have been checked more than a certain
     * number of times.
     *
     * @param msgExt Message to be discarded.
     */
    public abstract void resolveDiscardMsg(MessageExt msgExt);
}
