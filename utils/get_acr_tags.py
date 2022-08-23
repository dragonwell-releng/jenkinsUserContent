# -*- coding: utf-8 -*-
# This file is auto-generated, don't edit it. Thanks.
import sys

from typing import List

from alibabacloud_cr20181201.client import Client as cr20181201Client
from alibabacloud_tea_openapi import models as open_api_models
from alibabacloud_cr20181201 import models as cr_20181201_models
from alibabacloud_tea_util import models as util_models
from alibabacloud_tea_util.client import Client as UtilClient

import argparse

def args_parser():
    parser = argparse.ArgumentParser(description='Script for list aliyun acr images tags.')
    parser.add_argument('--endpoint', '-e', dest='endpoint', default='cr.cn-hangzhou.aliyuncs.com',
                        help='endpoint')
    parser.add_argument('--key', '-k', dest='access_key', default='',
                        help='access key')
    parser.add_argument('--password', '-p', dest='access_password', default='',
                        help='access password')
    parser.add_argument('--instance_id', '-ii', dest='instance_id', default='',
                        help='instance id')
    parser.add_argument('--repo_id', '-ri', dest='repo_id', default='',
                        help='repo id')
    parser.add_argument('--page_size', '-ps', dest='page_size', default=100,
                        help='page size')
    parser.add_argument('--page_no', '-pn', dest='page_no', default=1,
                        help='page no.')
    args = parser.parse_args()
    return args


class Sample:
    def __init__(self):
        pass

    @staticmethod
    def create_client(
        access_key_id: str,
        access_key_secret: str,
        endpoint: str,
    ) -> cr20181201Client:
        """
        使用AK&SK初始化账号Client
        @param access_key_id:
        @param access_key_secret:
        @return: Client
        @throws Exception
        """
        config = open_api_models.Config(
            access_key_id=access_key_id,
            access_key_secret=access_key_secret
        )
        config.endpoint = endpoint
        return cr20181201Client(config)

    @staticmethod
    def main(
        **kwargs,
    ) -> None:
        client = Sample.create_client(kwargs["key"], kwargs["pwd"], kwargs["endpoint"])
        list_repo_tag_request = cr_20181201_models.ListRepoTagRequest(
            instance_id=kwargs["instance_id"],
            repo_id=kwargs["repo_id"],
            page_size=kwargs["page_size"],
            page_no=kwargs["page_no"]
        )
        runtime = util_models.RuntimeOptions()
        try:
            print(client.list_repo_tag_with_options(list_repo_tag_request, runtime))
        except Exception as error:
            UtilClient.assert_as_string(error.message)


if __name__ == '__main__':
    args = args_parser().__dict__
    Sample.main(key=args["access_key"], pwd=args["access_password"],
                endpoint=args["endpoint"], instance_id=args["instance_id"],
                repo_id=args["repo_id"], page_size=args["repo_id"],
                page_no=args["repo_id"])
