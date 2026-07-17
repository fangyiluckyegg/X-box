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
-- 表的结构 `post`
--

CREATE TABLE `post` (
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
(6, '天黑了', 'images/photo6.png', 'dayday2018@qq.com', '2016-03-03 17:53:55', '<p>大家好，我是<span style="text-decoration: underline;"><em><strong>&ldquo;我是天亮了&rdquo;</strong></em></span>，很高兴能够与大家在这里相识，还希望以后大家多多关照！这是一条仅管理员可见的留言哦！</p>', 1);

-- --------------------------------------------------------

--
-- 表的结构 `reply`
--

CREATE TABLE `reply` (
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
