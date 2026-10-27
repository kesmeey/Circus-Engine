"""Describe static variable accesses using version-specific evidence; no state VM."""
from functools import lru_cache
import json
from pathlib import Path


@lru_cache(maxsize=None)
def state_profile(profile_id):
    # Explicit binding: another game's opcode profile does not grant these rules.
    if profile_id != 'dc3dd-a5a8':
        return None
    path = Path(__file__).resolve().parent.parent / 'profiles' / 'dc3dd-state.json'
    return json.loads(path.read_text(encoding='utf-8'))


def describe_access(profile_id, semantic, bank, index, direction):
    result = {'bank': bank, 'index': index, 'direction': direction}
    if semantic.get('runtime_variable_model') == 'DC2_PACKED':
        if bank < 0 or index < 0:
            return {**result, 'kind':'unsafe_native_index', 'rule':'reject_in_runtime'}
        if bank == 10:
            return {**result, 'kind':'persistent_alias', 'width':8,
                    'rule':'read_ordinary_byte' if direction=='read' else 'clamp_0_255_mirror_100' if index<=20 else 'write_ignored'}
        if bank == 100:
            return {**result, 'kind':'persistent_byte', 'width':8,
                    'rule':'read_contiguous_memory' if direction=='read' else 'low_8_bits_mirror_10_if_index_le_20' if index<100 else 'write_ignored'}
        if bank == 400 or 300 <= bank <= 310 or 200 <= bank <= 210:
            width=32 if 200<=bank<=210 else 16
            return {**result, 'kind':'packed_view', 'width':width, 'byte_order':'big', 'signed':True,
                    'rule':'read_signed' if direction=='read' else 'clamp_i16' if width==16 else 'store_i32_bytes',
                    'base_bank':100 if bank==400 else bank-(300 if width==16 else 200),
                    'max_index':99 if bank==400 else 98 if width==16 else 96}
        if bank in (96,97,98):
            return {**result,'kind':'persistent_bit','width':1,'rule':'lsb_first_read' if direction=='read' else 'set_if_nonzero'}
    ordinary = semantic['variables']['ordinary']
    if 0 <= bank <= ordinary['banks'][1] and 0 <= index <= ordinary['indices'][1]:
        return {**result, 'kind': 'ordinary_u8', 'width': 8,
                'rule': ordinary[direction]}
    if bank < 0 or (0 <= bank <= ordinary['banks'][1] and index < 0):
        return {**result, 'kind': 'unsafe_native_index', 'rule': 'reject_in_future_runtime'}
    state = state_profile(profile_id)
    if state and str(bank) in state['banks']:
        dispatch = state['banks'][str(bank)]
        entry = dispatch['entries'].get(str(index))
        rule = (entry or dispatch['default'])[direction]
        return {**result, 'kind': 'host_dispatch', 'rule': rule,
                'contract': f'{profile_id}/bank/{bank}/' + (str(index) if entry else 'default'),
                'status': entry['status'] if entry else 'static_verified_default'}
    return {**result, 'kind': 'unmapped', 'rule': 'no_cross_version_guess'}


def variable_accesses(record, profile_id, semantic):
    """Accesses of the recognized handler only, not an executed trace or host callees."""
    operation = record['operation']
    p = record.get('operands')
    accesses = []

    def add(bank, index, direction):
        accesses.append(describe_access(profile_id, semantic, bank, index, direction))

    if operation in ('variable.update','variable.update_indirect') and p[3] in (0, 1, 2):
        if p[3] != 0:
            add(p[0], p[1], 'read')
        add(p[0], p[1], 'write')
    elif operation == 'variable.update_indirect' and p[3] == 3:
        add(p[0],p[1],'read')
        # The second source index requires runtime state; never treat p1 as that index.
        record['indirect_read']={'bank':p[2],'index_from':{'bank':p[0],'index':p[1]},'result_register':'99:255'}
    elif operation in ('variable.copy', 'variable.add_variable'):
        if operation == 'variable.add_variable':
            add(p[0], p[1], 'read')
        add(p[2], p[3], 'read')
        add(p[0], p[1], 'write')
    elif operation == 'variable.sum_list':
        for term in record['terms']:
            add(term['bank'], term['index'], 'read')
        add(p[0], p[1], 'write')
    elif operation == 'branch.compare':
        add(p[0], p[1], 'read')
        if p[3] & 128:
            add(p[2] >> 8, p[2] & 255, 'read')
    return accesses
