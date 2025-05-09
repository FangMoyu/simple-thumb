package com.example.simplethumb.Controller;

import com.example.simplethumb.common.BaseResponse;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.common.ResultUtils;
import com.example.simplethumb.model.dto.thumb.DoThumbRequest;
import com.example.simplethumb.service.ThumbService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/thumb")
public class ThumbController {
    @Resource
    private ThumbService thumbService;

    private final Counter successCounter;

    private final Counter failureCounter;

    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        try {
            Boolean result = thumbService.doThumb(doThumbRequest, request);

            if(result) {
                successCounter.increment();
                return ResultUtils.success(true);
            } else {
                failureCounter.increment();
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
            }
        }catch (Exception e) {
            failureCounter.increment();
            return ResultUtils.error(e.getMessage());
        }


    }

    @PostMapping("/undo")
    public BaseResponse<Boolean> cancelThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.undoThumb(doThumbRequest, request);
        return ResultUtils.success(success);
    }

    public ThumbController(MeterRegistry registry) {
        this.successCounter = Counter.builder("thumb.success.count")
                .description("Total successful thumb")
                .register(registry);
        this.failureCounter = Counter.builder("thumb.failure.count")
                .description("Total failed thumb")
                .register(registry);
    }


}
