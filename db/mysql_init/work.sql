-- ============================================================
-- 【方案A·2026-07-20】原 db/class_init/work.sql 并入 mysql_init 顶层，
-- 使 MySQL 全新数据卷首次初始化自动建 work 库。
-- 背景：官方 MySQL 8.0 entrypoint 对 /docker-entrypoint-initdb.d/* 仅做【非递归】通配
--       （for f in /docker-entrypoint-initdb.d/*），子目录（如原 class_init/）内的 .sql
--       会被 "ignoring" 分支跳过，不会自动执行；故上移至此（initdb.d 顶层），
--       全新数据卷首次初始化即自动运行，无需在 MySQL 就绪后手动执行。
-- 幂等说明：CREATE DATABASE / CREATE TABLE 均带 IF NOT EXISTS，可重复执行无害；
--       表内数据 INSERT 与 AUTO_INCREMENT 设置保留原 phpMyAdmin dump 语义。
-- 账号授权：class_user 对 work.* 的授权由 db/mysql_scripts/docker-entrypoint-wrapper.sh
--       的 ensure_class_user() 在 MySQL 就绪后完成（晚于 initdb.d），本脚本仅建库/建表，
--       不依赖 class_user 账号，互不冲突。
-- 执行顺序：在 initdb.d 字母序中位于 init.sql / init.template.sql / migrate_role.sql / msg.sql
--       之后（文件名 w 开头），但 work 库自包含、无跨脚本依赖，顺序安全。
-- ============================================================
-- phpMyAdmin SQL Dump
-- version 4.5.3.1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: 2016-03-12 03:22:41
-- 服务器版本： 5.7.10-log
-- PHP Version: 5.6.17

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `work`
--
CREATE DATABASE IF NOT EXISTS `work` DEFAULT CHARACTER SET utf8 COLLATE utf8_general_ci;
USE `work`;

-- --------------------------------------------------------

--
-- 表的结构 `admin_user`
--

CREATE TABLE IF NOT EXISTS `admin_user` (
  `username` varchar(20) NOT NULL,
  `password` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- 转存表中的数据 `admin_user`
--

INSERT INTO `admin_user` (`username`, `password`) VALUES
('admin', '');

-- --------------------------------------------------------

--
-- 表的结构 `work_pic`
--

CREATE TABLE IF NOT EXISTS `work_pic` (
  `p_id` int(11) NOT NULL,
  `t_id` int(11) NOT NULL,
  `p_src` varchar(50) NOT NULL,
  `p_name` varchar(50) NOT NULL,
  `p_date` date NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- 转存表中的数据 `work_pic`
--

INSERT INTO `work_pic` (`p_id`, `t_id`, `p_src`, `p_name`, `p_date`) VALUES
(1, 1, 'pm1.jpg', '巧克力饼干广告', '2014-10-20'),
(2, 1, 'pm201.jpg', '运动品牌广告', '2014-10-25'),
(3, 1, 'pm3.jpg', '咖啡品牌宣传画册', '2015-01-05'),
(4, 1, 'pm4.jpg', '运动品牌推广宣传', '2015-01-15'),
(5, 2, 'w1.jpg', '手机宣传网站', '2015-01-16'),
(6, 2, 'w2.jpg', '个人展示网站', '2015-01-20'),
(7, 2, 'w3.jpg', '房地产网站', '2015-02-10'),
(8, 2, 'w4.jpg', '在线学习网站', '2015-02-24'),
(9, 3, 'f1.jpg', '卡通动漫作品', '2015-03-20'),
(10, 3, 'f2.jpg', '卡通形象宣传动画', '2015-03-25'),
(11, 3, 'f3.jpg', '网站开场动画', '2015-04-13'),
(12, 3, 'f4.jpg', 'Flash动画网站', '2015-05-20');

-- --------------------------------------------------------

--
-- 表的结构 `work_type`
--

CREATE TABLE IF NOT EXISTS `work_type` (
  `t_id` int(11) NOT NULL,
  `t_name` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- 转存表中的数据 `work_type`
--

INSERT INTO `work_type` (`t_id`, `t_name`) VALUES
(1, '平面设计'),
(2, '网页设计'),
(3, '动画设计');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `work_pic`
--
ALTER TABLE `work_pic`
  ADD PRIMARY KEY (`p_id`);

--
-- Indexes for table `work_type`
--
ALTER TABLE `work_type`
  ADD PRIMARY KEY (`t_id`);

--
-- 在导出的表使用AUTO_INCREMENT
--

--
-- 使用表AUTO_INCREMENT `work_pic`
--
ALTER TABLE `work_pic`
  MODIFY `p_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;
--
-- 使用表AUTO_INCREMENT `work_type`
--
ALTER TABLE `work_type`
  MODIFY `t_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
