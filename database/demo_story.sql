-- Navicat 执行入口 2/2：schema.sql 执行完成后，再按需运行本文件。
USE narrative_studio;
-- 先通过页面注册账户并创建项目，再将下面的变量替换为实际 ID 后执行。
SET @project_id = 1;

INSERT INTO state_variable(project_id, variable_key, display_name, value_type, initial_value, description)
VALUES (@project_id, 'trust_liu', '刘宇信任度', 'INTEGER', '30', '示例整数状态'),
       (@project_id, 'found_food_secret', '发现饭菜异常', 'BOOLEAN', 'false', '示例布尔状态');

INSERT INTO story_node(project_id, node_key, title, content, node_type, is_start, position_x, position_y)
VALUES (@project_id, 'start', '空教室', '你在陌生教室中醒来。', 'NORMAL', 1, 0, 0),
       (@project_id, 'corridor', '走廊', '走廊尽头传来脚步声。', 'NORMAL', 0, 320, 0),
       (@project_id, 'ending_leave', '离开', '你推开了出口的门。', 'ENDING', 0, 640, 0);

INSERT INTO story_choice(project_id, source_node_id, target_node_id, choice_text, sort_order)
SELECT @project_id, s.id, t.id, '离开教室', 1
FROM story_node s JOIN story_node t ON t.project_id=@project_id AND t.node_key='corridor'
WHERE s.project_id=@project_id AND s.node_key='start';
