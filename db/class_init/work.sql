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

CREATE TABLE `admin_user` (
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

CREATE TABLE `work_pic` (
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

CREATE TABLE `work_type` (
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
