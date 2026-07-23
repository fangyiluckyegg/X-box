-- ============================================================
-- 【方案A·2026-07-20】原 db/class_init/msg.sql 并入 mysql_init 顶层，
-- 使 MySQL 全新数据卷首次初始化自动建 msg 库。
-- 背景：官方 MySQL 8.0 entrypoint 对 /docker-entrypoint-initdb.d/* 仅做【非递归】通配
--       （for f in /docker-entrypoint-initdb.d/*），子目录（如原 class_init/）内的 .sql
--       会被 "ignoring" 分支跳过，不会自动执行；故上移至此（initdb.d 顶层），
--       全新数据卷首次初始化即自动运行，无需在 MySQL 就绪后手动执行。
-- 幂等说明：CREATE DATABASE / CREATE TABLE 均带 IF NOT EXISTS，可重复执行无害；
--       表内数据 INSERT 与 AUTO_INCREMENT 设置保留原 phpMyAdmin dump 语义。
-- 账号授权：class_user 对 msg.* 的授权由 db/mysql_scripts/docker-entrypoint-wrapper.sh
--       的 ensure_class_user() 在 MySQL 就绪后完成（晚于 initdb.d），本脚本仅建库/建表，
--       不依赖 class_user 账号，互不冲突。
-- 执行顺序：在 initdb.d 字母序中位于 init.sql / init.template.sql / migrate_role.sql
--       之后（文件名 m 开头），但 msg 库自包含、无跨脚本依赖，顺序安全。
-- ============================================================
-- phpMyAdmin SQL Dump
-- version 4.5.3.1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: 2016-03-12 03:21:22
-- 服务器版本： 5.7.10-log
-- PHP Version: 5.6.17

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `msg`
--
CREATE DATABASE IF NOT EXISTS `msg` DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
USE `msg`;

-- --------------------------------------------------------

--
-- 表的结构 `admin_user`
--

CREATE TABLE IF NOT EXISTS `admin_user` (
  `username` varchar(20) NOT NULL,
  `password` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 注：admin_user 种子数据改由容器启动脚本 Niu_Txl/ensure_admin_hash.php 负责建/修（bcrypt 化），此处不再写明文 INSERT。

-- --------------------------------------------------------

--
-- 表的结构 `post`
--

CREATE TABLE IF NOT EXISTS `post` (
  `P_ID` int(11) NOT NULL,
  `P_Name` varchar(30) NOT NULL,
  `P_Pic` varchar(30) NOT NULL,
  `P_Mail` varchar(30) NOT NULL,
  `P_Date` datetime NOT NULL,
  `P_Content` text NOT NULL,
  `P_Private` tinyint(4) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- 转存表中的数据 `post`
--

INSERT INTO `post` (`P_ID`, `P_Name`, `P_Pic`, `P_Mail`, `P_Date`, `P_Content`, `P_Private`) VALUES
(2, '海里的鱼', 'images/photo9.png', 'abc@163.com', '2016-03-03 17:42:47', '<p>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 这是我们的第一条留言吗？希望有更多的人在这里给我们留言，欢迎大家都来留言！这是我们的第一条留言吗？希望有更多的人在这里给我们留言，欢迎大家都来留言！</p>', 0),
(3, 'Make it good', 'images/photo5.png', '1123@qq.com', '2016-03-03 17:48:51', '<p>大家好，我是<span style="text-decoration: underline;"><em><strong>Make</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！</p>', 0),
(4, 'HHHHH', 'images/photo2.png', '22334455@163.com', '2016-03-03 17:51:11', '<p>大家好，我是<span style="text-decoration: underline;"><em><strong>HANA</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！</p>', 0),
(5, '我是老六', 'images/photo3.png', 'ABC@qq.com', '2016-03-03 17:52:44', '<p>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 大家好，我是<span style="text-decoration: underline;"><em><strong>&ldquo;我是ABC&rdquo;</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！大家好，我是<span style="text-decoration: underline;"><em><strong>&ldquo;我是ABC&rdquo;</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！</p>', 0),
(6, '天黑了', 'images/photo6.png', 'dayday2018@163.com', '2016-03-03 17:53:55', '<p>大家好，我是<span style="text-decoration: underline;"><em><strong>&ldquo;我是天亮了&rdquo;</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！这是一条仅管理员可见的留言哦！</p>', 1);

-- --------------------------------------------------------

--
-- 表的结构 `reply`
--

CREATE TABLE IF NOT EXISTS `reply` (
  `R_ID` int(11) NOT NULL,
  `R_Post` int(11) NOT NULL,
  `R_Name` varchar(30) NOT NULL,
  `R_Pic` varchar(30) NOT NULL,
  `R_Mail` varchar(30) NOT NULL,
  `R_Date` datetime NOT NULL,
  `R_Content` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- 转存表中的数据 `reply`
--

INSERT INTO `reply` (`R_ID`, `R_Post`, `R_Name`, `R_Pic`, `R_Mail`, `R_Date`, `R_Content`) VALUES
(1, 6, '海里的鱼', 'images/photo9.png', 'crh456@163.com', '2016-03-03 17:59:24', '<p>这是和管理员说啥呢？</p>');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `post`
--
ALTER TABLE `post`
  ADD PRIMARY KEY (`P_ID`);

--
-- Indexes for table `reply`
--
ALTER TABLE `reply`
  ADD PRIMARY KEY (`R_ID`);

--
-- 在导出的表使用AUTO_INCREMENT
--

--
-- 使用表AUTO_INCREMENT `post`
--
ALTER TABLE `post`
  MODIFY `P_ID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;
--
-- 使用表AUTO_INCREMENT `reply`
--
ALTER TABLE `reply`
  MODIFY `R_ID` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
