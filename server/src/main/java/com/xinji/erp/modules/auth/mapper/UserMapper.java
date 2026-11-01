package com.xinji.erp.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinji.erp.modules.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
