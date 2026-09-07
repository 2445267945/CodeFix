/*
 Navicat Premium Data Transfer

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 80035
 Source Host           : localhost:3306
 Source Schema         : code_fix

 Target Server Type    : MySQL
 Target Server Version : 80035
 File Encoding         : 65001

 Date: 07/09/2026 20:24:55
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `agent_chat_message`;
CREATE TABLE `agent_chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联Task，可为空',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联Run，可为空',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色：USER / ASSISTANT',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `created_at` bigint NOT NULL COMMENT '创建时间，毫秒时间戳',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_id`(`message_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_run_id`(`run_id`) USING BTREE,
  INDEX `idx_task_session`(`task_id`, `session_id`) USING BTREE,
  INDEX `idx_task_session_created`(`task_id`, `session_id`, `created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 734 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent多轮对话消息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_event
-- ----------------------------
DROP TABLE IF EXISTS `agent_event`;
CREATE TABLE `agent_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent消息唯一ID，用于消息幂等与链路追踪',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属审计任务ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属会话ID',
  `agent_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产生该事件的Agent名称',
  `parent_agent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父Agent名称，用于构建Agent调用层级',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属run唯一id',
  `event` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent事件类型，如THINK、TOOL_CALL、TOOL_RESULT、FINISH、ERROR',
  `step` int NOT NULL COMMENT '当前Agent自身的推理步骤，从1开始递增',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent当前状态，如THINKING、EXECUTING、FINISHED、ERROR',
  `output` json NULL COMMENT '事件输出内容，按event类型保存不同结构的JSON数据',
  `event_timestamp` bigint NOT NULL COMMENT '事件发生时间，Unix时间戳，毫秒',
  `action_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_id`(`message_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_task_agent`(`task_id`, `agent_name`) USING BTREE,
  INDEX `idx_task_timestamp`(`task_id`, `event_timestamp`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35726 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI Agent执行过程事件表，记录Agent推理、工具调用、工具结果及最终状态等事件' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_file_change
-- ----------------------------
DROP TABLE IF EXISTS `agent_file_change`;
CREATE TABLE `agent_file_change`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `diff_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `event_id` bigint NULL DEFAULT NULL,
  `file_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `operation` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `added_lines` int NULL DEFAULT 0,
  `removed_lines` int NULL DEFAULT 0,
  `diff_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_diff_id`(`diff_id`) USING BTREE,
  INDEX `idx_task_run`(`task_id`, `run_id`) USING BTREE,
  INDEX `idx_session`(`session_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 180 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_run
-- ----------------------------
DROP TABLE IF EXISTS `agent_run`;
CREATE TABLE `agent_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '一次Agent执行的唯一ID',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属任务ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属会话ID',
  `attempt` int NOT NULL COMMENT '当前任务的第几次执行尝试，从1开始',
  `status` int NOT NULL COMMENT '本次Run当前状态',
  `started_at` bigint NULL DEFAULT NULL COMMENT 'Run开始时间，Unix毫秒时间戳',
  `ended_at` bigint NULL DEFAULT NULL COMMENT 'Run结束时间，Unix毫秒时间戳',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '本次Run失败原因',
  `created_at` bigint NOT NULL COMMENT 'Run记录创建时间，Unix毫秒时间戳',
  `action_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `permission_profile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'WORKSPACE',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_run_id`(`run_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_task_attempt`(`task_id`, `attempt`) USING BTREE,
  INDEX `idx_task_status`(`task_id`, `status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 515 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent执行Run表，记录一次任务执行尝试的生命周期' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_run_state_history
-- ----------------------------
DROP TABLE IF EXISTS `agent_run_state_history`;
CREATE TABLE `agent_run_state_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `from_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `trigger_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `trigger` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `to_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `step` int NULL DEFAULT NULL,
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_id`(`run_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_message_id`(`message_id`) USING BTREE,
  INDEX `idx_run_created`(`run_id`, `created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4140 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_session
-- ----------------------------
DROP TABLE IF EXISTS `agent_session`;
CREATE TABLE `agent_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `workspace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作空间ID，第一版暂为空',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '一次对话的摘要',
  `created_at` bigint NOT NULL COMMENT '创建时间，毫秒时间戳',
  `updated_at` bigint NOT NULL COMMENT '更新时间，毫秒时间戳',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_id`(`session_id`) USING BTREE,
  INDEX `idx_workspace_id`(`workspace_id`) USING BTREE,
  INDEX `idx_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 259 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent会话' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_task
-- ----------------------------
DROP TABLE IF EXISTS `agent_task`;
CREATE TABLE `agent_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务唯一ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `version` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '1.0' COMMENT '任务协议版本',
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户原始问题或审计指令',
  `code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始Java代码',
  `smells` json NULL COMMENT '原始风险信息',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '当前执行run的唯一ID',
  `status` int NOT NULL COMMENT '当前任务状态',
  `created_at` bigint NOT NULL COMMENT '创建时间，Unix毫秒时间戳',
  `updated_at` bigint NOT NULL COMMENT '最后更新时间，Unix毫秒时间戳',
  `last_heartbeat_at` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_id`(`task_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 517 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI代码审计任务主表，保存任务当前状态及原始上下文' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workspace
-- ----------------------------
DROP TABLE IF EXISTS `agent_workspace`;
CREATE TABLE `agent_workspace`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作空间业务ID',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作空间名称',
  `root_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作空间根目录',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CREATING/READY/ERROR/DELETED',
  `created_at` bigint NOT NULL COMMENT '创建时间，毫秒时间戳',
  `updated_at` bigint NOT NULL COMMENT '更新时间，毫秒时间戳',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_workspace_id`(`workspace_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 205 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent工作空间' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
