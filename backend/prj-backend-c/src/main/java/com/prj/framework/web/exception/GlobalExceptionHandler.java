package com.prj.framework.web.exception;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.prj.common.core.domain.AjaxResult;
import com.prj.common.exception.ServiceException;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 业务逻辑异常
    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        Integer code = e.getCode();
        return code != null ? AjaxResult.error(code, e.getMessage()) : AjaxResult.error(e.getMessage());
    }

    // 处理运行期异常
    @ExceptionHandler(RuntimeException.class)
    public AjaxResult handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        // [P1-FIX] 不向客户端暴露异常详情，防止信息泄露
        return AjaxResult.error("系统内部错误，请联系管理员");
    }

    // 兜底处理异常
    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        // [P1-FIX] 不向客户端暴露异常详情，防止信息泄露
        return AjaxResult.error("系统内部错误，请联系管理员");
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public AjaxResult handleBindException(BindException e)
    {
        log.error(e.getMessage(), e);
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return AjaxResult.error(message);
    }

    /**
     * [P0-FIX] @RequestBody 参数校验异常处理
     * 当 @Valid 注解触发 JSR-303 校验失败时，返回第一个字段的错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.error("参数校验失败: {}", e.getMessage());
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return AjaxResult.error(message);
    }
}
