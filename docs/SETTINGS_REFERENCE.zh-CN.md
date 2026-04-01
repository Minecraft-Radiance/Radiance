# Radiance 设置参考（中文）

> 本文档覆盖 `src/main/resources/modules/*.yaml` 中当前暴露的全部模块设置。  
> 默认值以仓库当前版本为准；如果后续 YAML 变更，请同步更新本文档。

## 设置入口

游戏内打开路径：

- **选项**
- **视频设置**
- **Pipeline**
- **Radiance Settings**

进入后即可打开 Radiance 的统一画面设置界面。

## 阅读方式

- **默认值**：模块 YAML 里的当前默认值。
- **作用**：这个设置主要控制什么。
- **调整建议**：什么时候该调高 / 调低，或者推荐保持默认。

---

## 视频设置：画质等级

> 位置：**视频设置 -> Pipeline -> 画质等级**

`画质等级` 会联动调整**整条渲染管线**，而不是只改一两个数字。  
当前会覆盖这些模块 / 参数：

- **Ray Tracing**：反射次数、远景几何、远景材质、地形 meshing、merge span、GI 模式、反射材质分级、更新间隔、PBR 采样、SHaRC、基础辐亮度、直接/间接光
- **NRD**：历史长度、预模糊半径、最大模糊半径、命中距离重建、anti-firefly
- **Upscaler**：FSR/XeSS/DLSS 质量档，FSR 锐化
- **Tone Mapping**：曲线、自动曝光、测光模式、饱和度、白点

### 档位说明

| 档位 | 定位 | 说明 |
|---|---|---|
| `performance` | 优先帧率 | 远景简化更激进，但保留 **2 次** 反射，避免环境完全发黑。 |
| `balanced` | 默认推荐 | 当前默认档；兼顾画质、稳定性和帧数。 |
| `quality` | 优先画质 | 远景保真、反射与 GI 更完整，但 GPU / CPU 压力明显更高。 |

---

## DLSS 模块

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `mode` | `balanced` | 控制 DLSS 质量档位。 | 帧率不够用 `performance`；画质优先 `quality`；原生抗锯齿可用 `dlaa`。 |

---

## FSR Upscaler 模块

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `enable` | `true` | 是否启用 FSR3 Upscaler。 | 出问题先关掉排查。 |
| `quality_mode` | `balanced` | 控制内部渲染分辨率与最终输出分辨率的比例。 | 低端机器可降到 `balanced / performance`。 |
| `sharpness` | `0.7` | 锐化强度。 | 过高会有振铃/边缘发硬；过低会发糊。 |

---

## NRD 模块

> NRD 主要是降噪稳定性、拖影控制、历史长度、预模糊半径一类参数。  
> 如果你不知道怎么调，**优先保持默认**。

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `antilag_luminance_sigma_scale` | `4.0` | 亮度变化触发 anti-lag 的阈值尺度。 | 亮度闪烁明显时可略调低；误触发太多可调高。 |
| `antilag_luminance_sensitivity` | `3.0` | 亮度变化敏感度。 | 过高更容易重置历史，过低更容易拖影。 |
| `responsive_accumulation_roughness_threshold` | `0.0` | 粗糙度到达该阈值后更偏向响应式累积。 | 反射拖影明显时可略提高。 |
| `responsive_accumulation_min_accumulated_frame_num` | `3` | 响应式累积启动前需要的最少历史帧数。 | 动态场景多时可略减。 |
| `max_accumulated_frame_num` | `60` | 最大历史累积帧数。 | 调高更稳但更容易拖；调低更灵敏但更噪。 |
| `max_fast_accumulated_frame_num` | `3` | 快速路径历史上限。 | 过高容易残影。 |
| `max_stabilized_frame_num` | `63` | 稳定阶段历史长度上限。 | 静态画面噪点多时可调高。 |
| `history_fix_frame_num` | `3` | 历史修正使用的帧数。 | 一般保持默认。 |
| `history_fix_base_pixel_stride` | `14` | 历史修正基础像素步长。 | 越大越便宜，但修正更粗。 |
| `history_fix_alternate_pixel_stride` | `14` | 历史修正交替步长。 | 通常与上项一起调。 |
| `fast_history_clamping_sigma_scale` | `1.5` | 快速历史夹取强度。 | 拖影重可调低，闪烁多可调高。 |
| `diffuse_prepass_blur_radius` | `30.0` | 漫反射预模糊半径。 | 噪声大可调高；细节损失大可调低。 |
| `specular_prepass_blur_radius` | `50.0` | 镜面预模糊半径。 | 反射噪点多可调高；反射发糊可调低。 |
| `min_hit_distance_weight` | `0.1` | 命中距离参与权重的最小值。 | 一般保持默认。 |
| `min_blur_radius` | `1.0` | 最小模糊半径。 | 想保细节可略降。 |
| `max_blur_radius` | `100.0` | 最大模糊半径。 | 想压噪可提高；不想糊可降低。 |
| `lobe_angle_fraction` | `0.15` | 用于镜面 lobe 相似度判定。 | 主要影响反射稳定性。 |
| `roughness_fraction` | `0.15` | 粗糙度相似性权重。 | 反射面变化快时可略调。 |
| `plane_distance_sensitivity` | `0.02` | 平面距离差异敏感度。 | 边缘漏光/串面时可调高。 |
| `specular_probability_thresholds_for_mv_modification_min` | `0.5` | 修改运动矢量时的镜面概率下限。 | 一般成对与 max 一起调。 |
| `specular_probability_thresholds_for_mv_modification_max` | `0.9` | 修改运动矢量时的镜面概率上限。 | 一般保持默认。 |
| `firefly_suppressor_min_relative_scale` | `2.0` | Firefly 抑制阈值。 | 亮点/火花多可调低。 |
| `min_material_for_diffuse` | `4.0` | 进入漫反射处理的最小材质阈值。 | 高级调参，通常不动。 |
| `min_material_for_specular` | `4.0` | 进入镜面处理的最小材质阈值。 | 高级调参，通常不动。 |
| `checkerboard_mode` | `off` | 棋盘式输入模式。 | 如果输入不是棋盘渲染，保持 `off`。 |
| `enable_anti_firefly` | `true` | 是否启用 Firefly 抑制。 | 出现亮点闪烁时不要关。 |
| `hit_distance_reconstruction_mode` | `5x5` | 命中距离重建卷积核。 | 画面更稳但更糊：`5x5`；更锐利但可能更噪：`3x3 / off`。 |

---

## Post Render 模块

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `star_count` | `3000` | 星空粒子数量。 | 太多会增加后处理开销。 |
| `star_min_size` | `0.5` | 星星最小尺寸。 | 调太低会不明显。 |
| `star_max_size` | `0.7` | 星星最大尺寸。 | 调太高会显得假。 |
| `star_radius` | `400.0` | 星空分布半径。 | 影响视觉空间感，性能影响较小。 |

---

## Ray Tracing 模块

> 这是当前最核心的一组设置。  
> 如果目标是“**尽量稳**”，推荐从默认值开始，只调下面几项：
>
> - `far_field_start_distance_chunks`
> - `far_field_material_mode`
> - `terrain_update_interval_frames`
> - `entity_update_interval_frames`
> - `num_ray_bounces`
> - `pbr_sampling_mode`

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `shader_pack_path` | `""` | 自定义光追 shader pack 路径；为空时使用内置包。 | 只有在自定义 shader pack 时填写。 |
| `world_representation_mode` | `triangle_blas` | 世界主要表示方式：传统三角形 BLAS 或 Chunk AABB。 | 目前稳定优先用 `triangle_blas`。 |
| `chunk_traversal_mode` | `triangle_hit` | Chunk 内求交/遍历模式。 | 目前稳定优先用 `triangle_hit`。 |
| `chunk_data_layout` | `triangle_geometry` | Chunk 数据组织形式。 | 当前 native 主路径优先用 `triangle_geometry`。 |
| `chunk_macrocell_size` | `disabled` | 宏块加速尺寸。 | 实验性，非 Route-B 调试时建议关闭。 |
| `terrain_meshing_mode` | `legacy_quads` | 地形面片预处理模式。 | 当前默认回退到稳定的 `legacy_quads`；实验性优化可手动试 `coplanar_merge / greedy_meshing`。 |
| `greedy_merge_max_span` | `16` | Greedy/共面合并的最大跨度。 | 只有启用合并模式时才明显生效。 |
| `blas_inclusion_mode` | `opaque_and_shadow` | 哪些几何进入 BLAS。 | 稳定和性能都较均衡，推荐保持默认。 |
| `glass_path_mode` | `special_path` | 玻璃走 BLAS、特殊路径或排除。 | 玻璃表现异常时可切回 `blas`。 |
| `foliage_path_mode` | `blas` | 树叶/植物等 cutout 几何走哪条路径。 | 当前默认回退到 `blas`，优先保证稳定和完整显示。 |
| `decoration_path_mode` | `blas` | 装饰物几何走哪条路径。 | 当前默认回退到 `blas`，优先保证稳定。 |
| `far_field_geometry_mode` | `simplified_shell` | 远景几何简化方式。 | 远处块很多时默认就很值；若想保真可改 `exact_chunks`。 |
| `far_field_start_distance_chunks` | `24` | 从多少个 chunk 之外开始按“远景”处理。 | 性能不够就减小；远景细节太差就增大。 |
| `far_field_material_mode` | `flat_surface` | 远景材质是完整 PBR 还是扁平平面材质。 | 追求性能建议 `flat_surface`；追求一致性用 `full_pbr`。 |
| `reflection_ray_material_mode` | `water_glass_metal` | 哪些材质值得打反射光线。 | 低成本推荐默认；全材质反射更贵。 |
| `diffuse_gi_mode` | `low_cost_hybrid` | 漫反射 GI 的预算模型。 | 默认更适合游戏运行；画质极限再用 `full_ray_tracing`。 |
| `separate_entity_terrain_accel_structures` | `true` | 实体和地形是否分离更新/加速结构。 | 推荐开启。 |
| `terrain_update_interval_frames` | `8` | 地形重建最小帧间隔。 | 卡 CPU 可调高；更新不及时可调低。 |
| `entity_update_interval_frames` | `1` | 实体重放缓存的刷新帧间隔。 | 卡顿时可适当调高，但会引入位置滞后。 |
| `block_entity_update_interval_frames` | `1` | 方块实体重放缓存刷新间隔。 | 动态方块实体异常时不要调太高。 |
| `particle_update_interval_frames` | `1` | 粒子重放缓存刷新间隔。 | 性能优先可调高。 |
| `num_ray_bounces` | `2` | 光线反弹次数。 | 性能不够时优先降到 `2`；极限压性能才建议 `1`。 |
| `use_jitter` | `true` | 是否启用抖动采样。 | 关闭更稳定但更容易锯齿/噪声固定。 |
| `pbr_sampling_mode` | `bilinear` | PBR 贴图采样模式。 | `nearest` 更快更锐；`bilinear` 更平滑。 |
| `transparent_split_mode` | `deterministic` | 透明路径分裂策略。 | 排查不稳定行为时优先 `deterministic`。 |
| `direct_light_strength` | `1.0` | 直接光贡献强度。 | 过亮就降，过暗就升。 |
| `indirect_light_strength` | `16.0` | 间接光贡献强度。 | 场景偏灰/过曝时优先调整这里。 |
| `basic_radiance` | `5.0` | 基础辐亮度底噪。 | 太低画面死黑；太高会发灰。 |
| `atmosphere_planet_radius` | `6360.0` | 大气模型的行星半径。 | 通常不改。 |
| `atmosphere_top_radius` | `6460.0` | 大气顶高度。 | 通常不改。 |
| `rayleigh_scale_height` | `8.0` | 瑞利散射尺度高度。 | 影响天空过渡和远景蓝雾。 |
| `mie_scale_height` | `1.2` | 米氏散射尺度高度。 | 影响雾感、太阳附近泛白。 |
| `rayleigh_scattering_coefficient` | `0.000005802,0.000013558,0.0000331` | 瑞利散射系数 RGB。 | 高级调参，不熟悉建议别动。 |
| `mie_anisotropy` | `0.8` | 米氏散射各向异性。 | 越高越偏向前向散射。 |
| `mie_scattering_coefficient` | `0.000021,0.000021,0.000021` | 米氏散射系数 RGB。 | 高级调参。 |
| `minimum_view_cosine` | `0.02` | 天体/大气相关的最小视角余弦。 | 主要影响天体大小/可见范围。 |
| `sun_radiance` | `8.0,8.0,8.0` | 太阳辐亮度。 | 太阳太刺眼时降低。 |
| `moon_radiance` | `0.24,0.3,0.6` | 月亮辐亮度。 | 夜景太暗/太蓝可微调。 |
| `use_sharc` | `true` | 是否启用 SHaRC。 | 新 shader pack 或异常时可关掉排查。 |
| `sharc_debug_mode` | `off` | SHaRC 调试输出模式。 | 只有调试时才开。 |

---

## Tone Mapping 模块

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `method` | `pbr_neutral` | 色调映射曲线。 | 默认最中性；想电影感可试 `aces / uncharted2`。 |
| `middle_grey` | `0.18` | 自动曝光的中灰目标。 | 画面整体偏亮/偏暗时可微调。 |
| `exposure_up_speed` | `8.0` | 变亮时曝光调整速度。 | 越高适应越快。 |
| `exposure_down_speed` | `8.0` | 变暗时曝光调整速度。 | 越高适应越快。 |
| `log2_luminance_min` | `-12.0` | 自动曝光统计下限。 | 极暗场景可略降。 |
| `log2_luminance_max` | `4.0` | 自动曝光统计上限。 | 极亮场景可略升。 |
| `low_percent` | `0.005` | 直方图低百分位截断。 | 可降低暗部异常值影响。 |
| `high_percent` | `0.99` | 直方图高百分位截断。 | 可降低高光异常值影响。 |
| `min_exposure` | `0.01` | 自动曝光最小值。 | 防止过暗。 |
| `max_exposure` | `2.0` | 自动曝光最大值。 | 防止过曝。 |
| `enable_auto_exposure` | `true` | 是否启用自动曝光。 | 想固定亮度时关闭。 |
| `exposure_metering_mode` | `center` | 曝光测光模式。 | 主体在中间时用 `center`；整体场景用 `global`。 |
| `center_metering_percent` | `20.0` | 中心测光区域占比。 | 越小越聚焦中央。 |
| `manual_exposure` | `1.0` | 关闭自动曝光时的手动曝光值。 | 仅在 auto exposure 关闭时生效。 |
| `exposure_bias` | `0.0` | 曝光偏移。 | 比大改曲线更适合做整体微调。 |
| `white_point` | `32.0` | 某些曲线的白点。 | 高光压缩不舒服时可调。 |
| `saturation` | `1.0` | 饱和度。 | 想更淡/更艳可调。 |
| `clamp_output` | `true` | 是否钳制输出范围。 | 正常建议开启。 |

---

## XeSS 模块

| 设置 | 默认值 | 作用 | 调整建议 |
|---|---:|---|---|
| `enable` | `true` | 是否启用 XeSS。 | 平台不兼容时关闭。 |
| `quality_mode` | `balanced` | XeSS 质量档位。 | 低端机可降到 `balanced / performance`。 |
| `pre_exposure` | `1.0` | XeSS 使用的预曝光值。 | 只有在曝光链不稳定时才需要改。 |

---

## Temporal Accumulation 模块

当前版本 **没有暴露可调设置**。
