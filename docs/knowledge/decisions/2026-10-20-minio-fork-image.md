tags: [docker, minio, supply-chain]
Decision: compose uses pgsty/minio pinned by release tag and sha256 index digest, because minio/minio is no longer pullable on Docker Hub or Quay.
Publisher is the pgsty community fork; revisit when an official image or another supported source exists.
