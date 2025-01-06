package com.example.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LclPriceMapper extends BaseMapper {

    void insertOrUpdate(@Param("data") Map<String, Object> data);

    LclPriceEntity getLclPriceByParams(@Param("pol") String pol, @Param("sort_type") String sort_type, @Param("destination") String destination, @Param("product") String product, @Param("speed_mode") String speed_mode);


    LclPriceEntity getLclPriceByParamsAWD(@Param("pol") String pol, @Param("destination") String destination, @Param("product") String product);
    List<Map<String, Object>> getAll();
}