package txtfnnl.pipelines;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.UIMAException;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;

import txtfnnl.uima.analysis_component.BioLemmatizerAnnotator;
import txtfnnl.uima.analysis_component.GazetteerAnnotator;
import txtfnnl.uima.analysis_component.GeneAnnotator;
import txtfnnl.uima.analysis_component.GeniaTaggerAnnotator;
import txtfnnl.uima.analysis_component.NOOPAnnotator;
import txtfnnl.uima.analysis_component.RelationshipFilter;
import txtfnnl.uima.analysis_component.SentenceFilter;
import txtfnnl.uima.analysis_component.SyntaxPatternAnnotator;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.analysis_component.opennlp.TokenAnnotator;
import txtfnnl.uima.collection.RelationshipWriter;
import txtfnnl.uima.resource.GnamedGazetteerResource;
import txtfnnl.uima.resource.LineBasedStringArrayResource;

/**
 * A pattern-based gene relationship extraction pipeline.
 * <p>
 * Input files can be read from a directory or listed explicitly, while output lines are written to
 * some directory or to STDOUT. Relationships are written on a single line. A relationship consists
 * of the document URL, the relationship type, the actor entity ID, the target entity ID, and the
 * sentence evidence where the relationship was found, all separated by tabs.
 * <p>
 * The default setup assumes gene and/or protein entities found in a <a
 * href="https://github.com/fnl/gnamed">gnamed</a> database.
 * 
 * @author Florian Leitner
 */
public class GeneRelationshipExtractor extends Pipeline {
  static final String DEFAULT_DATABASE = "gnamed";
  static final String DEFAULT_JDBC_DRIVER = "org.postgresql.Driver";
  static final String DEFAULT_DB_PROVIDER = "postgresql";
  /** Select all human, mouse, and rat gene and protein symbols by their Entrez IDs. */
  static final String DEFAULT_SQL_QUERY = "SELECT gr.accession, g.species_id, ps.value "
      + "FROM gene_refs AS gr, genes AS g, genes2proteins AS g2p, protein_strings AS ps "
      + "WHERE gr.namespace = 'gi' AND gr.id = g.id AND g.species_id IN (9606, 10090, 10116) AND g.id = g2p.gene_id AND g2p.protein_id = ps.id AND ps.cat = 'symbol' "
      + "UNION SELECT gr.accession, g.species_id, gs.value "
      + "FROM gene_refs AS gr, genes AS g, gene_strings AS gs "
      + "WHERE gr.namespace = 'gi' AND gr.id = g.id AND g.species_id IN (9606, 10090, 10116) AND gr.id = gs.id AND gs.cat = 'symbol'";
  // + "AND g.species_id IN (9606, 10090, 10116) AND s.cat = 'symbol' AND r.namespace = 'gi'";
  // TODO: make this next thing a parameter (SQL query files?)
  static final String MAMMALIAN_TF_IDS = "('10002', '100072', '100073351', '10009', '100128927', '100129885', '100131390', '100132074', '100182', '100187701', '100271849', '100361818', '100361836', '10062', '100978', '101023', '10113', '10127', '10153', '10172', '10194', '10215', '102423', '102442', '10260', '10265', '10308', '10320', '10357', '10363', '10365', '10379', '103836', '103889', '104156', '104338', '104360', '104382', '104394', '1044', '1045', '1046', '10472', '10481', '10485', '10488', '1050', '1051', '1052', '10522', '1053', '10538', '1054', '10553', '10608', '106143', '10620', '106389', '10655', '10660', '10661', '10664', '10691', '10716', '10725', '10732', '10736', '10743', '107503', '107586', '107587', '107751', '10782', '10795', '10865', '108655', '108961', '109032', '109575', '109663', '109889', '109910', '109947', '109948', '11016', '110521', '110593', '11063', '110648', '110685', '110784', '110794', '110796', '110805', '11083', '11107', '11108', '1112', '11166', '112400', '112770', '11278', '11279', '11281', '112939', '11317', '113931', '113983', '113984', '114090', '114109', '114142', '114208', '114213', '114485', '114498', '114499', '114500', '114501', '114502', '114503', '114505', '114519', '114565', '114604', '114634', '114712', '114845', '11545', '11568', '11569', '115768', '116039', '116113', '11614', '11622', '11634', '116448', '116490', '116543', '116544', '116545', '116557', '116639', '116648', '116651', '116668', '116674', '116810', '11694', '11695', '117034', '117058', '117062', '117095', '117107', '117140', '117154', '117232', '117282', '117560', '11792', '11819', '11835', '118445', '11859', '11863', '11864', '11865', '11878', '11906', '11908', '11909', '11910', '11911', '11921', '11922', '11923', '11924', '11925', '12013', '12014', '12020', '12022', '12023', '120237', '12029', '12053', '121340', '12142', '121536', '121551', '121599', '121643', '12173', '12224', '122953', '12355', '12393', '12394', '12399', '124411', '12590', '12591', '12592', '12606', '12607', '12608', '12609', '12611', '12677', '127343', '12753', '12785', '128209', '128408', '128553', '12912', '12913', '12915', '12916', '12951', '13017', '13018', '13047', '13048', '130497', '130557', '1316', '13170', '13172', '13198', '132625', '13390', '13392', '13393', '13394', '13395', '13396', '134187', '13496', '13555', '13557', '13559', '13560', '13591', '13592', '13593', '135935', '136259', '13626', '13653', '13654', '13655', '13656', '13661', '13709', '13710', '13711', '13712', '13713', '13714', '137814', '13796', '13797', '13798', '13799', '13806', '13813', '13819', '1385', '1386', '13864', '13865', '138715', '13875', '13876', '1388', '1389', '1390', '139628', '13982', '13983', '13984', '14008', '14009', '14011', '14013', '14025', '14030', '140477', '140489', '140586', '140587', '140588', '140589', '1406', '140628', '140690', '14106', '142', '14233', '14234', '14235', '14236', '14237', '14238', '14239', '14240', '14241', '14247', '14281', '14282', '14283', '14284', '14390', '144455', '14460', '14461', '14462', '14463', '14464', '14465', '14472', '145258', '14531', '14536', '14581', '14582', '145873', '14632', '14633', '14634', '147912', '14815', '148198', '1482', '148327', '14836', '14842', '14843', '1488', '148979', '14912', '15110', '15111', '15191', '15205', '15206', '15207', '15208', '15209', '15214', '15218', '15220', '15221', '15223', '15227', '15228', '15229', '1523', '15242', '15248', '15251', '152518', '15273', '15284', '153222', '153572', '15361', '15364', '15370', '15371', '15373', '15375', '15376', '15377', '15378', '15379', '15384', '15387', '15394', '15395', '15396', '15398', '15399', '15400', '15401', '15402', '15403', '15404', '15405', '15407', '15408', '15410', '15412', '15413', '15414', '15415', '15416', '15417', '15422', '15423', '15424', '15425', '15426', '15429', '15430', '15431', '15433', '15434', '15436', '15437', '15438', '15460', '15499', '15500', '155061', '155430', '156726', '157848', '15900', '159296', '15936', '15951', '161452', '162239', '1628', '163059', '16362', '16363', '16364', '16371', '16372', '16373', '16391', '16392', '16468', '16476', '16477', '16478', '1649', '165', '16549', '16596', '16597', '16598', '16599', '16600', '16601', '16656', '16658', '167826', '16814', '168374', '16842', '168544', '168620', '16869', '16870', '16871', '16872', '16874', '16876', '16909', '16917', '16918', '16969', '16978', '169792', '170302', '170574', '170671', '170729', '170820', '170825', '170909', '170959', '171017', '171018', '171068', '171078', '171137', '17119', '17121', '17122', '17125', '17126', '17127', '17128', '17129', '171299', '17130', '171302', '17132', '17133', '17134', '17135', '171355', '171356', '171360', '171454', '17172', '17173', '17187', '17188', '17258', '17259', '17260', '17261', '17268', '17285', '17286', '17292', '17293', '17300', '17301', '17341', '17342', '17425', '17428', '1745', '1746', '1747', '1748', '1749', '1750', '17536', '1761', '17681', '17701', '17702', '17764', '17765', '17859', '17863', '17864', '17865', '17869', '17876', '17877', '17878', '17927', '17928', '17932', '17933', '18012', '18013', '18014', '18018', '18019', '18021', '18022', '18023', '18024', '18025', '18027', '18028', '18029', '18030', '18032', '18033', '18034', '18044', '18046', '18071', '18072', '18088', '18089', '18091', '18092', '18094', '18095', '18096', '18109', '18124', '18142', '18143', '18171', '18181', '18185', '1820', '18227', '18291', '18420', '18423', '18424', '18426', '18503', '18504', '18505', '18506', '18507', '18508', '18509', '18510', '18511', '18514', '18515', '18516', '18609', '18612', '18616', '18617', '18667', '1869', '1870', '1871', '18736', '1874', '18740', '18741', '18742', '1875', '1876', '1877', '18771', '1879', '18933', '18935', '18986', '18987', '18988', '18991', '18992', '18993', '18994', '18996', '18997', '18998', '18999', '190', '19009', '19013', '19015', '19016', '19127', '19130', '192109', '192110', '19213', '192274', '19290', '19291', '19377', '19401', '19411', '19434', '194655', '195333', '195733', '1958', '195828', '1959', '196', '1960', '1961', '19664', '19668', '19696', '19697', '19698', '19712', '19724', '19725', '19726', '19821', '19883', '19885', '1997', '1998', '1999', '2000', '2001', '2002', '200350', '2004', '2005', '201516', '2016', '2018', '20181', '20182', '20183', '20186', '2019', '2020', '20204', '2023', '20230', '20231', '20289', '2034', '20371', '20375', '20429', '20464', '20465', '20471', '20472', '20473', '20474', '20475', '20476', '20482', '20583', '20585', '20613', '2063', '20658', '20664', '20665', '20666', '20667', '20668', '20669', '20670', '20671', '20672', '20674', '20675', '20677', '20678', '20679', '20680', '20681', '20682', '20683', '20687', '20688', '20689', '20728', '2077', '207785', '2078', '20787', '20788', '20807', '208076', '208104', '20841', '20846', '20847', '20848', '20849', '20850', '20851', '20852', '208647', '208677', '20893', '209446', '209448', '209707', '2099', '20997', '2100', '2101', '2103', '2104', '210719', '2113', '211323', '2114', '2115', '211586', '2116', '2117', '2118', '2119', '2120', '2122', '212712', '2130', '21349', '21375', '21380', '21384', '21385', '21386', '21387', '21388', '21389', '21405', '21406', '21407', '21408', '21410', '214105', '21411', '21412', '21413', '21414', '21415', '21416', '214162', '21417', '21418', '21419', '21420', '21422', '21423', '21425', '21426', '21428', '21454', '214855', '215418', '216285', '21674', '21676', '21677', '21679', '21685', '217082', '217166', '21769', '21780', '21781', '218030', '21804', '21807', '218100', '21815', '21833', '21834', '21847', '21869', '218772', '21907', '21908', '21909', '219150', '219409', '220202', '22025', '22026', '22059', '22062', '22157', '22160', '22165', '221833', '221937', '22221', '222546', '22259', '22260', '22278', '22282', '222894', '223227', '22337', '22344', '223669', '223843', '223922', '22431', '22433', '224656', '225497', '225631', '225872', '225998', '226049', '226075', '22608', '22632', '22634', '22640', '22642', '226442', '22661', '226641', '22666', '22671', '22685', '226896', '22694', '22696', '22697', '22701', '22702', '22712', '22724', '22751', '22754', '22755', '22757', '22758', '227631', '22764', '22767', '22771', '22772', '22773', '22774', '22778', '22779', '22780', '22797', '22806', '22807', '22809', '22823', '22834', '228598', '228662', '22877', '22882', '228829', '22887', '228880', '22890', '228911', '228913', '2290', '229004', '229007', '22903', '229055', '22926', '2294', '22947', '2295', '2296', '2297', '2298', '2299', '2300', '230025', '2301', '230119', '230162', '2302', '2303', '23036', '2304', '23040', '2305', '23051', '230587', '2306', '2307', '230700', '2308', '230824', '2309', '23090', '231044', '23119', '2313', '23152', '231991', '232430', '23261', '23269', '232791', '232879', '232906', '232934', '233060', '23314', '23316', '233410', '234219', '23440', '23493', '23512', '23528', '2353', '235320', '2354', '2355', '23613', '23660', '237336', '23764', '237911', '238455', '23849', '23856', '23857', '238673', '23871', '23872', '239099', '239546', '23958', '23967', '240690', '241066', '24113', '24136', '241514', '24208', '24209', '242509', '24252', '24253', '242705', '24309', '24330', '24333', '24356', '24370', '243833', '24388', '243931', '243937', '24413', '244219', '24452', '24453', '24457', '244579', '24459', '24460', '244713', '244813', '24508', '24516', '24517', '24518', '24522', '245368', '245572', '245583', '245595', '24566', '245671', '24577', '245980', '246073', '246086', '246149', '246264', '246271', '246282', '246301', '24631', '246334', '246361', '246760', '24705', '24706', '24790', '24817', '24831', '24842', '24873', '24883', '24890', '24916', '24918', '24919', '2494', '25094', '25098', '25099', '25100', '25124', '25125', '25126', '25129', '25148', '25149', '25154', '25157', '25159', '2516', '25162', '25172', '25221', '25231', '25242', '25243', '25271', '252856', '252880', '252885', '252886', '252915', '252917', '252924', '252973', '25301', '25334', '25337', '253738', '25389', '25401', '25410', '254251', '25431', '25445', '25446', '25483', '25492', '25509', '2551', '25517', '25546', '25554', '255877', '25591', '25607', '25620', '25628', '256297', '25631', '256380', '25640', '25646', '25664', '25671', '25672', '25682', '25690', '25695', '257', '25701', '25705', '25714', '25720', '25735', '25747', '257634', '25803', '25806', '25833', '259241', '25988', '260298', '260325', '26137', '26205', '2623', '2624', '2625', '26257', '2626', '2627', '26296', '26298', '2636', '2637', '26379', '26380', '26381', '26386', '26423', '26424', '26427', '26468', '2649', '26508', '26584', '266680', '266716', '266734', '266738', '266743', '266787', '266792', '266813', '2672', '268297', '268417', '268469', '268564', '26910', '26927', '269389', '269401', '26959', '269800', '270004', '27022', '27023', '27049', '27056', '27080', '27086', '27164', '27217', '272322', '272382', '27287', '27319', '27324', '2735', '2736', '2737', '27386', '277353', '282825', '282840', '283078', '283150', '284695', '286101', '286380', '287074', '287076', '287185', '287361', '287423', '287441', '287469', '287521', '287615', '287638', '287647', '287940', '288105', '288151', '288152', '288153', '288154', '288353', '288457', '288665', '288736', '288774', '288821', '288906', '289026', '289048', '289201', '289230', '289311', '289595', '289752', '289754', '28999', '290221', '290317', '290749', '290765', '2908', '2909', '291100', '291105', '291228', '29148', '291908', '291960', '29203', '292060', '29227', '29228', '292301', '292404', '292721', '29279', '2928', '292820', '292892', '292934', '292935', '29300', '293012', '293017', '293038', '293046', '293104', '293165', '293405', '293537', '293568', '29357', '29361', '29362', '293624', '293701', '29394', '293992', '29410', '294322', '294328', '294395', '294515', '29452', '294562', '29458', '294640', '29467', '294924', '29508', '295248', '295297', '295315', '295327', '29535', '295475', '295568', '29560', '29567', '295695', '29577', '29588', '29589', '295965', '29609', '296201', '296272', '296281', '296320', '296344', '296374', '296399', '296420', '296478', '296499', '296500', '296510', '296511', '29657', '296706', '296953', '29721', '297480', '297705', '297769', '297783', '297804', '29808', '298084', '298189', '298316', '298400', '29841', '29842', '298449', '298506', '298555', '29861', '298732', '298878', '298894', '299016', '299061', '299138', '299186', '299206', '299210', '299499', '299766', '299897', '299979', '300054', '30009', '300095', '300260', '30046', '30051', '30062', '300807', '300947', '300954', '301038', '301121', '301284', '301285', '301476', '301570', '302327', '302333', '302369', '302400', '302415', '302582', '302583', '302811', '302946', '302962', '302993', '303164', '303188', '303226', '303310', '303398', '303399', '303469', '303480', '303488', '303489', '303491', '303496', '303499', '303511', '303698', '303753', '303828', '303836', '303970', '303991', '304005', '304054', '304056', '304063', '304071', '304092', '304103', '304127', '304275', '304298', '304342', '304479', '304514', '304666', '304729', '304741', '304786', '304815', '304850', '304935', '304947', '304962', '305066', '305083', '305311', '305471', '305494', '305501', '305584', '305589', '305719', '305848', '305854', '305858', '305896', '305897', '305968', '305972', '305999', '306110', '306168', '306330', '306400', '306657', '306659', '306695', '3068', '306862', '306873', '306977', '307217', '307498', '307547', '307715', '307721', '307740', '307794', '307806', '307820', '307829', '307834', '307839', '307919', '30812', '308126', '30818', '308311', '30832', '308336', '30834', '308363', '308366', '308387', '3084', '308406', '308411', '308435', '308482', '308523', '308582', '308607', '3087', '308766', '308900', '3090', '309002', '309031', '309095', '3091', '309164', '309165', '30923', '30927', '30928', '309288', '309389', '30942', '309430', '30944', '309452', '309457', '3096', '3097', '309728', '309859', '309871', '309888', '309900', '309957', '310375', '310616', '310659', '310660', '311071', '311093', '311311', '311414', '311462', '311505', '311508', '311547', '311575', '311604', '311615', '311658', '311721', '311723', '311742', '311764', '311832', '311876', '311901', '312195', '312203', '312303', '312331', '312451', '312777', '312897', '312936', '3131', '313166', '313219', '313504', '313507', '313512', '313554', '313557', '313558', '313575', '313666', '313678', '313913', '313978', '313993', '313994', '3142', '314221', '314246', '314322', '314374', '314423', '314436', '314539', '314586', '314616', '314638', '314675', '314711', '314818', '314988', '315039', '315081', '315101', '315305', '315308', '315309', '315333', '315337', '315338', '315341', '315497', '315532', '315601', '315606', '315689', '315804', '315870', '315882', '3159', '316052', '316102', '316164', '316214', '316327', '316351', '3164', '3166', '316626', '316742', '3169', '3170', '3171', '3172', '317268', '317376', '317382', '3174', '3175', '317715', '3182', '3190', '3195', '3196', '319758', '3198', '3199', '3200', '3201', '320145', '3202', '320267', '3203', '3204', '3205', '320522', '3206', '3207', '320799', '3209', '320995', '3211', '3212', '3213', '3214', '3215', '3216', '3217', '3218', '3219', '3221', '3222', '3223', '3224', '3226', '3227', '3229', '3231', '3232', '3233', '3234', '3235', '3236', '3237', '3239', '326', '328', '3280', '3297', '3298', '3299', '329934', '332221', '332937', '333929', '3344', '337868', '338917', '339344', '3394', '339488', '339559', '340069', '340385', '340784', '3428', '342909', '3431', '343472', '344018', '346389', '347853', '3516', '353088', '353187', '353227', '353305', '360204', '360389', '360482', '360551', '360588', '360610', '360631', '360635', '3607', '360737', '360775', '360858', '360896', '360905', '360960', '360961', '361060', '361095', '361096', '361131', '361400', '361508', '361544', '361576', '361627', '361630', '361668', '361746', '361784', '361801', '361809', '361816', '361944', '361946', '362085', '362106', '362125', '362165', '362176', '362268', '362280', '362286', '362291', '362339', '362391', '362453', '362464', '362530', '362591', '362665', '362675', '362686', '362733', '362741', '362871', '362896', '362925', '362948', '363165', '363185', '363243', '363595', '363632', '363656', '363667', '363672', '363684', '363735', '363831', '364069', '364081', '364140', '364152', '3642', '364418', '364510', '364680', '364706', '364712', '364855', '364883', '364910', '3651', '365299', '365371', '365564', '365744', '365748', '3659', '365900', '3660', '366078', '3661', '366126', '366142', '3662', '366210', '366214', '366229', '3663', '366340', '3664', '3665', '366542', '366598', '366829', '366949', '366951', '366954', '366960', '366964', '366996', '366998', '367', '3670', '367092', '367100', '367106', '367152', '367218', '367264', '367314', '367832', '367846', '367944', '367950', '368057', '368158', '3720', '3725', '3726', '3727', '378435', '380912', '380993', '381319', '381359', '381373', '381463', '381549', '381990', '382066', '382074', '382639', '383491', '385674', '387609', '388112', '388566', '388585', '389058', '389549', '389692', '390010', '390259', '390874', '390992', '391723', '3975', '399489', '399823', '4005', '4009', '401', '4010', '402381', '404281', '405', '406', '406164', '406169', '4084', '4086', '4087', '4088', '4089', '4090', '4091', '4094', '4097', '414065', '4149', '4150', '4205', '4207', '4208', '4209', '4211', '4212', '4222', '4223', '4286', '429', '4297', '430', '4303', '4306', '432731', '4330', '4335', '433938', '434174', '436240', '440097', '442425', '4487', '4488', '4520', '4601', '4602', '4603', '4605', '4609', '4610', '4613', '4617', '4618', '463', '4654', '4656', '466', '4661', '467', '468', '474', '4760', '4761', '4762', '4772', '4773', '4774', '4775', '4776', '4778', '4779', '4780', '4781', '4782', '4783', '4784', '4790', '4791', '4799', '4800', '4802', '4807', '4808', '4821', '4824', '4825', '4849', '4861', '4862', '4899', '4901', '4904', '4929', '494344', '497984', '497985', '497986', '497987', '497988', '498194', '498407', '49854', '498545', '498575', '498607', '498633', '498864', '498918', '498945', '498952', '4990', '499016', '499090', '499146', '499171', '499361', '499362', '499380', '499497', '499510', '499593', '499874', '499900', '499951', '499952', '499964', '500023', '500037', '500048', '500110', '500126', '500129', '500131', '500137', '500156', '500235', '500239', '500538', '500558', '500574', '500818', '500950', '500964', '500981', '500982', '501099', '501145', '5013', '5015', '501506', '5017', '502759', '502886', '502946', '50496', '50524', '50545', '50552', '50554', '50659', '50662', '50674', '5075', '5076', '5077', '5078', '5079', '50794', '50796', '5080', '50804', '5081', '5083', '50862', '5087', '5089', '5090', '50907', '50913', '50914', '50943', '50945', '51043', '51085', '51157', '51176', '51193', '51222', '51230', '51270', '51274', '51341', '51351', '51385', '51450', '51513', '51533', '51621', '5178', '51804', '51886', '5241', '52615', '52679', '52712', '5307', '5308', '5309', '5316', '5324', '5325', '5326', '53314', '53335', '53404', '53415', '53417', '53626', '5396', '53970', '54006', '54123', '54131', '54139', '541457', '54254', '54264', '54267', '54276', '54278', '54284', '54345', '54352', '54367', '54422', '54446', '5449', '5451', '5452', '5453', '5454', '545474', '5455', '5456', '5457', '5458', '54585', '5459', '5460', '54601', '54626', '5463', '5465', '5467', '5468', '54711', '54713', '54738', '54796', '54897', '54937', '550619', '55079', '55274', '55502', '55509', '55553', '55565', '55578', '55634', '55657', '55806', '55810', '55840', '55893', '55897', '55900', '55922', '55927', '56033', '56198', '56218', '56259', '5626', '5629', '56380', '56449', '56458', '56461', '56484', '56490', '56501', '56676', '56711', '56722', '56734', '56751', '56787', '56790', '56805', '56809', '56938', '56956', '56980', '56987', '56995', '57057', '571', '57167', '57215', '57233', '57246', '57343', '57432', '57473', '57592', '57593', '57594', '57615', '57616', '57621', '57623', '57659', '57693', '57765', '57801', '57822', '579', '5813', '5814', '58158', '58180', '58198', '58208', '58495', '58500', '58805', '58820', '58842', '58850', '58851', '58852', '58853', '58921', '58954', '59016', '59057', '59058', '59112', '5914', '5915', '5916', '59269', '59328', '59335', '59336', '59348', '5966', '5970', '5971', '5978', '5989', '5990', '5991', '5992', '5993', '60329', '60349', '60351', '60394', '604', '60446', '60447', '6045', '60462', '60468', '60529', '60563', '60611', '60661', '60685', '6095', '6096', '6097', '619575', '619665', '6239', '6256', '6257', '6258', '6297', '6299', '6304', '630579', '63876', '639', '63973', '63974', '63976', '63977', '63978', '64067', '641339', '64186', '64188', '64288', '64290', '64321', '64332', '64344', '64345', '643609', '64376', '64379', '64406', '64412', '644168', '64441', '64444', '64530', '64572', '646', '64618', '64619', '64628', '64637', '64641', '64651', '6473', '6474', '64764', '64826', '64843', '64864', '64919', '6492', '6493', '6495', '6496', '6498', '65035', '65050', '65100', '65161', '65193', '65199', '653404', '653427', '654496', '6591', '6596', '65986', '66019', '66136', '6615', '66277', '664783', '664799', '6651', '6656', '6657', '6658', '6659', '6660', '6662', '6663', '6664', '66642', '6665', '6666', '6667', '6668', '6670', '6671', '668', '66830', '66867', '6688', '6689', '66953', '67143', '67150', '6720', '6721', '6722', '67255', '6736', '6772', '6773', '6774', '6775', '6776', '6777', '67778', '6778', '679028', '679571', '679701', '679712', '679869', '680117', '680201', '680427', '680620', '680712', '680751', '681092', '681359', '683504', '684085', '685072', '685102', '685360', '685732', '686117', '6862', '687', '68750', '688', '68837', '68842', '6886', '688699', '688822', '688999', '689030', '68910', '689210', '689695', '689788', '6899', '689918', '68992', '689988', '690430', '690820', '6909', '6910', '6911', '6913', '691398', '691556', '691842', '69228', '6925', '69257', '6926', '6927', '6928', '6929', '6932', '6934', '6935', '6936', '6938', '6939', '6940', '6942', '6943', '6945', '69743', '69890', '7003', '7004', '7008', '70127', '7019', '7020', '7021', '7022', '7023', '7024', '7025', '7026', '7027', '7029', '7030', '7041', '7050', '70508', '7067', '70673', '7068', '7071', '70779', '7080', '70998', '7101', '71041', '71137', '71371', '71375', '7157', '71591', '7161', '71702', '71722', '7181', '7182', '71838', '71950', '72057', '72154', '7227', '72465', '72567', '72739', '727857', '727940', '72843', '7287', '7288', '728957', '7291', '7294', '73181', '73191', '73389', '7342', '7376', '7391', '7392', '74007', '74120', '74123', '74164', '7421', '74352', '74434', '74451', '745', '74533', '74561', '7490', '7494', '7528', '7539', '7541', '7543', '7544', '7545', '7546', '7547', '75507', '7552', '7553', '7554', '7555', '7556', '75580', '7566', '7567', '7570', '7571', '7572', '75753', '7584', '7587', '75871', '7589', '7592', '7593', '7594', '7596', '7629', '7634', '76365', '7637', '7639', '7643', '7644', '7678', '7690', '7691', '7692', '7693', '7694', '7695', '7697', '7699', '7702', '7704', '7707', '7709', '7710', '7711', '7712', '7716', '7718', '7727', '7728', '77286', '7730', '7732', '7741', '7743', '7746', '7753', '7755', '7761', '7762', '7764', '7767', '7773', '77771', '7799', '78266', '78284', '7849', '78912', '78968', '78972', '78974', '790969', '79116', '79190', '79191', '79192', '79225', '79237', '79240', '79241', '79245', '79255', '79362', '79365', '79401', '7942', '79431', '79618', '79692', '79733', '7975', '79759', '79800', '79923', '79977', '80034', '8013', '8022', '80317', '80320', '80338', '8061', '80709', '80712', '80714', '80720', '80778', '80859', '80902', '8091', '8092', '8110', '81518', '81524', '81566', '81646', '81647', '81703', '81710', '81717', '81736', '81808', '81812', '81813', '81817', '81819', '8187', '81879', '8193', '81931', '8320', '8328', '83383', '83395', '83396', '83439', '83463', '83474', '83482', '83498', '83574', '83586', '83595', '83618', '83619', '83630', '83632', '83635', '83726', '83741', '83807', '83826', '83855', '83879', '83881', '83925', '83990', '83993', '84017', '8403', '84046', '84107', '84108', '84159', '84295', '84382', '84385', '84410', '84449', '84482', '84504', '84524', '84528', '8456', '84574', '84619', '8462', '8463', '84653', '84654', '84662', '84667', '84699', '84838', '84839', '84878', '84891', '84911', '84969', '8521', '8531', '8538', '85416', '85424', '85434', '8545', '85471', '85489', '85497', '85508', '8553', '8570', '860', '8609', '861', '8626', '864', '8726', '8820', '8848', '8856', '8880', '8928', '8929', '8939', '89884', '90316', '90333', '9095', '9096', '90987', '90993', '91752', '9189', '91975', '9208', '9242', '9314', '9355', '93691', '93730', '93837', '93986', '94187', '94188', '9421', '94222', '94234', '9464', '9480', '9489', '9496', '9516', '9519', '9572', '9575', '9580', '9586', '9592', '9603', '9640', '9705', '9745', '9753', '9774', '9839', '988', '9915', '9925', '9935', '99377', '9970', '9971', '9975', '9988', '12151', '12400', '15901', '15902', '15903', '161882', '17131', '19017', '23414', '4092', '672', '9612')";
  /** Select all human, mouse, and rat gene and protein TF symbols by their UniProt accessions. */
  static final String MAMMALIAN_TF_QUERY = "SELECT pr.accession, p.species_id, ps.value " +
      "FROM gene_refs AS gr, genes2proteins AS g2p, proteins AS p, protein_refs AS pr, protein_strings AS ps " +
      "WHERE gr.namespace = 'gi' AND gr.accession IN " +
      MAMMALIAN_TF_IDS +
      " AND gr.id = g2p.gene_id AND g2p.protein_id = p.id AND p.id = pr.id AND pr.namespace = 'uni' AND p.id = ps.id AND ps.cat = 'symbol' " +
      "UNION SELECT pr.accession, g.species_id, gs.value " +
      "FROM gene_refs AS gr, genes AS g, gene_strings AS gs, genes2proteins AS g2p, protein_refs AS pr " +
      "WHERE gr.namespace = 'gi' AND gr.accession IN " +
      MAMMALIAN_TF_IDS +
      " AND gr.id = g.id AND gr.id = gs.id AND gr.id = g2p.gene_id AND g2p.protein_id = pr.id AND pr.namespace = 'uni' AND gs.cat = 'symbol'";

  private GeneRelationshipExtractor() {
    throw new AssertionError("n/a");
  }

  public static ExternalResourceDescription getGazetteerResource(CommandLine cmd, Logger l,
      String querySql, boolean idMatching, boolean exactCaseMatching, String defaultDriverClass,
      String defaultProvider, String defaultDbName) throws ResourceInitializationException,
      ClassNotFoundException {
    // driver class name
    final String driverClass = cmd.getOptionValue('D', defaultDriverClass);
    Class.forName(driverClass);
    // db url
    final String dbHost = cmd.getOptionValue('H', "localhost");
    final String dbProvider = cmd.getOptionValue('P', defaultProvider);
    final String dbName = cmd.getOptionValue('d', defaultDbName);
    final String dbUrl = String.format("jdbc:%s://%s/%s", dbProvider, dbHost, dbName);
    l.log(Level.INFO, "JDBC URL: {0}", dbUrl);
    // create builder
    GnamedGazetteerResource.Builder b = GnamedGazetteerResource.configure(dbUrl, driverClass,
        querySql);
    b.idMatching();
    // set username/password options
    if (cmd.hasOption('u')) b.setUsername(cmd.getOptionValue('u'));
    if (cmd.hasOption('p')) b.setPassword(cmd.getOptionValue('p'));
    return b.create();
  }

  public static void main(String[] arguments) {
    final CommandLineParser parser = new PosixParser();
    final Options opts = new Options();
    CommandLine cmd = null;
    Pipeline.addLogHelpAndInputOptions(opts);
    Pipeline.addTikaOptions(opts);
    Pipeline.addJdbcResourceOptions(opts, DEFAULT_JDBC_DRIVER, DEFAULT_DB_PROVIDER,
        DEFAULT_DATABASE);
    Pipeline.addOutputOptions(opts);
    // sentence splitter options
    opts.addOption("S", "successive-newlines", false, "split sentences on successive newlines");
    opts.addOption("s", "single-newlines", false, "split sentences on single newlines");
    // sentence filter options
    opts.addOption("f", "filter-sentences", true, "retain sentences using a file of regex matches");
    opts.addOption("F", "filter-remove", false, "filter removes sentences with matches");
    // tokenizer options setup
    opts.addOption("G", "genia", true,
        "use GENIA (with the dir containing 'morphdic/') instead of OpenNLP");
    // semantic patterns - REQUIRED!
    opts.addOption("p", "patterns", true, "match sentences with semantic patterns");
    // actor and target ID, name pairs
    opts.addOption("a", "actor-sql", true, "SQL query that produces actor ID, name pairs");
    opts.addOption("t", "target-sql", true, "SQL query that produces target ID, name pairs");
    try {
      cmd = parser.parse(opts, arguments);
    } catch (final ParseException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    final Logger l = Pipeline.loggingSetup(cmd, opts,
        "txtfnnl grex [options] -p <patterns> <directory|files...>\n");
    // sentence splitter
    String splitSentences = null; // S, s
    if (cmd.hasOption('s')) {
      splitSentences = "single";
    } else if (cmd.hasOption('S')) {
      splitSentences = "successive";
    }
    // sentence filter
    final File sentenceFilterPatterns = cmd.hasOption('f') ? new File(cmd.getOptionValue('f'))
        : null;
    final boolean removingSentenceFilter = cmd.hasOption('F');
    // (GENIA) tokenizer
    final String geniaDir = cmd.getOptionValue('G');
    // semantic patterns
    File patterns = null;
    try {
      patterns = new File(cmd.getOptionValue('p'));
    } catch (NullPointerException e) {
      l.severe("no patterns file");
      System.err.println("patterns file missing");
      System.exit(1); // == EXIT ==
    }
    // entity queries
    final String actorSQL = cmd.hasOption('a') ? cmd.getOptionValue('a') : MAMMALIAN_TF_QUERY;
    final String targetSQL = cmd.hasOption('t') ? cmd.getOptionValue('t') : DEFAULT_SQL_QUERY;
    // DB resource
    ExternalResourceDescription actorGazetteer = null;
    ExternalResourceDescription targetGazetteer = null;
    try {
      actorGazetteer = getGazetteerResource(cmd, l, actorSQL, true, false, DEFAULT_JDBC_DRIVER,
          DEFAULT_DB_PROVIDER, DEFAULT_DATABASE);
      targetGazetteer = getGazetteerResource(cmd, l, targetSQL, true, false, DEFAULT_JDBC_DRIVER,
          DEFAULT_DB_PROVIDER, DEFAULT_DATABASE);
    } catch (final ResourceInitializationException e) {
      System.err.println("JDBC resoruce setup failed:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    } catch (final ClassNotFoundException e) {
      System.err.println("JDBC driver class unknown:");
      System.err.println(e.toString());
      System.exit(1); // == EXIT ==
    }
    // output (format)
    final String encoding = Pipeline.outputEncoding(cmd);
    final File outputDirectory = Pipeline.outputDirectory(cmd);
    final boolean overwriteFiles = Pipeline.outputOverwriteFiles(cmd);
    try {
      ExternalResourceDescription patternResource = LineBasedStringArrayResource.configure(
          "file:" + patterns.getCanonicalPath()).create();
      // 0:tika, 1:splitter, 2:filter, 3:tokenizer, 4:lemmatizer, 5:patternMatcher,
      // 6:actor gazetteer, 7:target gazetteer, 8:filter regulator 9: filter target
      final Pipeline rex = new Pipeline(10);
      rex.setReader(cmd);
      rex.configureTika(cmd);
      rex.set(1, SentenceAnnotator.configure(splitSentences));
      if (sentenceFilterPatterns == null) rex.set(2, NOOPAnnotator.configure());
      else rex.set(2, SentenceFilter.configure(sentenceFilterPatterns, removingSentenceFilter));
      if (geniaDir == null) {
        rex.set(3, TokenAnnotator.configure());
        rex.set(4, BioLemmatizerAnnotator.configure());
      } else {
        rex.set(3, GeniaTaggerAnnotator.configure().setDirectory(new File(geniaDir)).create());
        // the GENIA Tagger already lemmatizes; nothing to do
        rex.set(4, NOOPAnnotator.configure());
      }
      rex.set(5, SyntaxPatternAnnotator.configure(patternResource).removeUnmatched().create());
      rex.set(6, GeneAnnotator.configure("UniProt", actorGazetteer).setTextNamespace("actor")
          .setTextIdentifier("regulator").create());
      rex.set(7, GeneAnnotator.configure("Entrez", targetGazetteer).setTextNamespace("actor")
          .setTextIdentifier("target").create());
      rex.set(8, RelationshipFilter.configure(SyntaxPatternAnnotator.URI, "event", "tre",
          SyntaxPatternAnnotator.URI, "actor", "regulator", GazetteerAnnotator.URI, "UniProt",
          null, false));
      rex.set(9, RelationshipFilter.configure(SyntaxPatternAnnotator.URI, "event", "tre",
          SyntaxPatternAnnotator.URI, "actor", "target", GazetteerAnnotator.URI, "Entrez", null,
          false));
      rex.setConsumer(RelationshipWriter.configure(outputDirectory, encoding,
          outputDirectory == null, overwriteFiles, true, null, true, true));
      rex.run();
    } catch (final UIMAException e) {
      l.severe(e.toString());
      l.log(Level.INFO, "StackTrace", e);
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    } catch (final IOException e) {
      l.severe(e.toString());
      l.log(Level.INFO, "StackTrace", e);
      System.err.println(e.getLocalizedMessage());
      System.exit(1); // == EXIT ==
    }
    System.exit(0);
  }
}